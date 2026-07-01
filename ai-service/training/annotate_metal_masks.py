"""
Generate weak metal/artifact segmentation masks for head CT volumes.

This script turns CQ500-style DICOM folders, zip archives, or NIfTI volumes into
a segmentation-ready layout:

    output/
      images_nii/              original CT volumes, one file per case
      labels_nii/              generated masks with the same stem as images
      previews/                optional mask overlay montages
      annotation_manifest.csv  case-level QC and label summary

Mask modes:
    binary   : 0=background, 1=metal core or nearby streak artifact
    combined : 0=background, 1=streak artifact, 2=metal core

The masks are weak labels generated from HU thresholds, proximity to high-HU
metal cores, gradient filtering, and 3D morphology. They are useful for metal
artifact segmentation pretraining and QC, but they are not lesion masks.
"""

from __future__ import annotations

import argparse
import csv
import tempfile
import zipfile
from pathlib import Path

import numpy as np
import SimpleITK as sitk
from scipy import ndimage
from tqdm import tqdm

sitk.ProcessObject_SetGlobalWarningDisplay(False)


HEAD_THRESHOLD = -200
METAL_CORE_LO = 1400
METAL_CORE_HI = 3500
STREAK_LO = -2000
STREAK_HI = -350
NEAR_METAL_MM = 12.0
GRAD_THRESHOLD = 200
OPEN_RADIUS = 2
CLOSE_RADIUS = 3
MIN_COMPONENT_VOXELS = 50

SMALL_COVERAGE = 0.05
MODERATE_COVERAGE = 0.50
MAX_REASONABLE_COVERAGE_PCT = 3.0
MAX_REASONABLE_SLICE_RATIO = 0.60


def safe_case_id(name: str) -> str:
    cleaned = name.replace(".nii.gz", "").replace(".nii", "")
    cleaned = cleaned.replace(" ", "_").replace("-", "_")
    return "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in cleaned).strip("_")


def extract_zip(zip_path: Path, dest: Path) -> Path:
    case_root = dest / zip_path.stem
    case_root.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(case_root)
    return case_root


def score_series_dir(path: Path) -> tuple[int, int, str]:
    name = path.name.upper()
    score = 0
    if "PLAIN" in name or "PRE CONTRAST" in name or "NON CONTRAST" in name:
        score += 120
    elif "HELICAL" in name:
        score += 40
    if "5MM" in name or "55MM" in name:
        score += 25
    elif "3MM" in name:
        score += 18
    elif "2.5" in name or "2.55" in name:
        score += 10
    if "BONE" in name:
        score -= 160
    if "D3D" in name:
        score -= 120
    if "THIN" in name or "0.625" in name or "0.62" in name:
        score -= 40
    if "CONTRAST" in name and "PRE CONTRAST" not in name and "NON CONTRAST" not in name:
        score -= 90
    return score, -len(name), name


def find_dicom_series_dirs(case_dir: Path) -> list[Path]:
    dirs = sorted({p.parent for p in case_dir.rglob("*.dcm")})
    if dirs:
        return dirs
    return sorted({p.parent for p in case_dir.rglob("*") if p.is_file()})


def load_best_dicom_series(case_dir: Path) -> tuple[sitk.Image, np.ndarray, str]:
    candidates: list[tuple[tuple[int, int, str], int, Path, str]] = []
    for dcm_dir in find_dicom_series_dirs(case_dir):
        series_ids = sitk.ImageSeriesReader.GetGDCMSeriesIDs(str(dcm_dir)) or []
        if not series_ids:
            files = sorted(str(p) for p in dcm_dir.glob("*.dcm"))
            if files:
                candidates.append((score_series_dir(dcm_dir), len(files), dcm_dir, ""))
            continue
        for sid in series_ids:
            files = sitk.ImageSeriesReader.GetGDCMSeriesFileNames(str(dcm_dir), sid)
            candidates.append((score_series_dir(dcm_dir), len(files), dcm_dir, sid))

    if not candidates:
        raise FileNotFoundError(f"No DICOM series found under {case_dir}")

    candidates.sort(key=lambda item: (item[0], item[1]), reverse=True)
    _, _, best_dir, best_sid = candidates[0]
    reader = sitk.ImageSeriesReader()
    if best_sid:
        files = reader.GetGDCMSeriesFileNames(str(best_dir), best_sid)
    else:
        files = sorted(str(p) for p in best_dir.glob("*.dcm"))
    reader.SetFileNames(files)
    image = reader.Execute()
    volume = sitk.GetArrayFromImage(image).astype(np.float32)
    if volume.ndim == 2:
        volume = volume[np.newaxis]
    return image, volume, best_dir.name


def load_nifti(path: Path) -> tuple[sitk.Image, np.ndarray, str]:
    image = sitk.ReadImage(str(path))
    volume = sitk.GetArrayFromImage(image).astype(np.float32)
    if volume.ndim == 2:
        volume = volume[np.newaxis]
    return image, volume, "nifti"


def largest_component(mask: np.ndarray) -> np.ndarray:
    labeled, num = ndimage.label(mask)
    if num == 0:
        return mask.astype(bool)
    sizes = ndimage.sum(mask, labeled, range(1, num + 1))
    return labeled == (int(np.argmax(sizes)) + 1)


def build_head_mask(volume: np.ndarray) -> np.ndarray:
    head = volume > HEAD_THRESHOLD
    if not np.any(head):
        return np.ones_like(volume, dtype=bool)
    head = largest_component(head)
    struct = ndimage.generate_binary_structure(3, 1)
    head = ndimage.binary_closing(head, structure=ndimage.iterate_structure(struct, 2))
    head = ndimage.binary_fill_holes(head)
    head = ndimage.binary_dilation(head, structure=struct, iterations=1)
    return head.astype(bool)


def nearby_mask(core_mask: np.ndarray, spacing_xyz: tuple[float, float, float]) -> np.ndarray:
    if not np.any(core_mask):
        return np.zeros_like(core_mask, dtype=bool)
    spacing_zyx = (spacing_xyz[2], spacing_xyz[1], spacing_xyz[0])
    distance = ndimage.distance_transform_edt(~core_mask.astype(bool), sampling=spacing_zyx)
    return distance <= NEAR_METAL_MM


def cleanup_mask(mask: np.ndarray) -> np.ndarray:
    mask = mask.astype(np.uint8)
    if OPEN_RADIUS > 0:
        struct = ndimage.iterate_structure(ndimage.generate_binary_structure(3, 1), OPEN_RADIUS)
        mask = ndimage.binary_opening(mask, structure=struct).astype(np.uint8)
    if CLOSE_RADIUS > 0:
        struct = ndimage.iterate_structure(ndimage.generate_binary_structure(3, 1), CLOSE_RADIUS)
        mask = ndimage.binary_closing(mask, structure=struct).astype(np.uint8)
    if MIN_COMPONENT_VOXELS > 0:
        labeled, num = ndimage.label(mask)
        if num > 0:
            sizes = ndimage.sum(mask, labeled, range(1, num + 1))
            for i, size in enumerate(sizes, start=1):
                if size < MIN_COMPONENT_VOXELS:
                    mask[labeled == i] = 0
    return mask.astype(np.uint8)


def generate_metal_artifact_mask(
    volume: np.ndarray,
    spacing_xyz: tuple[float, float, float],
    mode: str,
) -> np.ndarray:
    head = build_head_mask(volume)
    core = ((volume >= METAL_CORE_LO) & (volume <= METAL_CORE_HI) & head)
    streak = ((volume >= STREAK_LO) & (volume <= STREAK_HI) & head)
    streak &= nearby_mask(core, spacing_xyz)
    union = (core | streak).astype(np.uint8)

    if GRAD_THRESHOLD > 0:
        gz = ndimage.sobel(volume, axis=0)
        gy = ndimage.sobel(volume, axis=1)
        gx = ndimage.sobel(volume, axis=2)
        grad = np.sqrt(gx * gx + gy * gy + gz * gz)
        union = (union & (grad >= GRAD_THRESHOLD)).astype(np.uint8)

    union = cleanup_mask(union)
    if mode == "binary":
        return union

    metal = ((union > 0) & core).astype(np.uint8)
    artifact = ((union > 0) & ~core).astype(np.uint8)
    combined = np.zeros_like(union, dtype=np.uint8)
    combined[artifact > 0] = 1
    combined[metal > 0] = 2
    return combined


def coverage_label(mask: np.ndarray) -> str:
    coverage = float((mask > 0).sum()) / float(mask.size) * 100.0 if mask.size else 0.0
    if coverage == 0:
        return "normal"
    if coverage < SMALL_COVERAGE:
        return "small_metal"
    if coverage < MODERATE_COVERAGE:
        return "moderate_metal"
    return "severe_metal"


def save_nifti_array(array: np.ndarray, reference: sitk.Image, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    image = sitk.GetImageFromArray(array)
    image.CopyInformation(reference)
    sitk.WriteImage(image, str(out_path))


def save_preview(volume: np.ndarray, mask: np.ndarray, out_path: Path, case_id: str) -> None:
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except Exception as exc:
        print(f"[warn] preview skipped for {case_id}: {exc}")
        return

    out_path.parent.mkdir(parents=True, exist_ok=True)
    nz = volume.shape[0]
    n_show = min(8, nz)
    per_slice = (mask > 0).reshape(nz, -1).sum(axis=1)
    if np.any(per_slice):
        indices = np.argsort(per_slice)[::-1][:n_show]
        indices = np.array(sorted(int(i) for i in indices), dtype=int)
    else:
        indices = np.linspace(0, nz - 1, n_show, dtype=int)

    wl, ww = 40, 80
    vmin, vmax = wl - ww / 2, wl + ww / 2
    fig, axes = plt.subplots(2, n_show, figsize=(n_show * 2.2, 4.6))
    if n_show == 1:
        axes = np.asarray(axes).reshape(2, 1)
    fig.suptitle(case_id, fontsize=10)
    for col, z in enumerate(indices):
        axes[0, col].imshow(volume[z], cmap="gray", vmin=vmin, vmax=vmax)
        axes[0, col].set_title(f"z={z}", fontsize=7)
        axes[0, col].axis("off")

        axes[1, col].imshow(volume[z], cmap="gray", vmin=vmin, vmax=vmax)
        overlay = mask[z]
        if np.any(overlay):
            rgba = np.zeros((*overlay.shape, 4), dtype=np.float32)
            rgba[overlay == 1, 0] = 1.0
            rgba[overlay == 1, 3] = 0.65
            rgba[overlay == 2, 2] = 1.0
            rgba[overlay == 2, 3] = 0.65
            axes[1, col].imshow(rgba)
        axes[1, col].axis("off")
    axes[0, 0].set_ylabel("CT", fontsize=8)
    axes[1, 0].set_ylabel("Mask", fontsize=8)
    plt.tight_layout()
    fig.savefig(out_path, dpi=120, bbox_inches="tight")
    plt.close(fig)


def iter_cases(input_dir: Path) -> list[Path]:
    archives = sorted(input_dir.glob("*.zip"))
    niftis = sorted(p for p in input_dir.iterdir() if p.name.endswith((".nii", ".nii.gz")))
    dicom_dirs = sorted(p for p in input_dir.iterdir() if p.is_dir())
    if archives or niftis:
        return archives + niftis + dicom_dirs
    return [input_dir]


def annotate_case(case_path: Path, output_dir: Path, mask_mode: str, save_previews: bool) -> dict[str, object]:
    case_id = safe_case_id(case_path.stem if case_path.is_file() else case_path.name)
    images_dir = output_dir / "images_nii"
    labels_dir = output_dir / "labels_nii"
    previews_dir = output_dir / "previews"
    image_out = images_dir / f"{case_id}.nii.gz"
    mask_out = labels_dir / f"{case_id}.nii.gz"
    preview_out = previews_dir / f"{case_id}.png"

    result: dict[str, object] = {
        "case_id": case_id,
        "source": str(case_path),
        "series_used": "",
        "image_nii": str(image_out),
        "label_nii": str(mask_out),
        "preview_png": str(preview_out) if save_previews else "",
        "n_slices": 0,
        "shape_zyx": "",
        "spacing_xyz": "",
        "mask_mode": mask_mode,
        "mask_voxels": 0,
        "metal_voxels": 0,
        "artifact_voxels": 0,
        "coverage_pct": 0.0,
        "affected_slices": 0,
        "severity_label": "normal",
        "qc_flag": "pass",
        "qc_notes": "",
        "status": "ok",
        "error": "",
    }

    try:
        with tempfile.TemporaryDirectory() as tmp:
            source = case_path
            if case_path.suffix.lower() == ".zip":
                source = extract_zip(case_path, Path(tmp))

            if source.name.endswith((".nii", ".nii.gz")):
                image, volume, series = load_nifti(source)
            else:
                image, volume, series = load_best_dicom_series(source)

            spacing_xyz = tuple(float(v) for v in image.GetSpacing())
            mask = generate_metal_artifact_mask(volume, spacing_xyz, mask_mode)
            image_out.parent.mkdir(parents=True, exist_ok=True)
            sitk.WriteImage(image, str(image_out))
            save_nifti_array(mask.astype(np.uint8), image, mask_out)
            if save_previews:
                save_preview(volume, mask, preview_out, case_id)

        positive = mask > 0
        coverage = float(positive.sum()) / float(mask.size) * 100.0 if mask.size else 0.0
        affected = int(np.any(positive, axis=(1, 2)).sum()) if mask.ndim == 3 else int(np.any(positive))
        notes: list[str] = []
        if coverage > MAX_REASONABLE_COVERAGE_PCT:
            notes.append("coverage_too_high")
        if mask.shape[0] and affected / mask.shape[0] > MAX_REASONABLE_SLICE_RATIO:
            notes.append("too_many_slices")

        result.update({
            "series_used": series,
            "n_slices": int(volume.shape[0]),
            "shape_zyx": "x".join(str(int(v)) for v in volume.shape),
            "spacing_xyz": "x".join(f"{v:.3f}" for v in spacing_xyz),
            "mask_voxels": int(positive.sum()),
            "metal_voxels": int((mask == 2).sum()) if mask_mode == "combined" else int(positive.sum()),
            "artifact_voxels": int((mask == 1).sum()) if mask_mode == "combined" else 0,
            "coverage_pct": round(coverage, 4),
            "affected_slices": affected,
            "severity_label": coverage_label(mask),
            "qc_flag": "review" if notes else "pass",
            "qc_notes": ";".join(notes),
        })
    except Exception as exc:
        result["status"] = "skip"
        result["error"] = str(exc)
    return result


def write_manifest(records: list[dict[str, object]], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest = output_dir / "annotation_manifest.csv"
    if not records:
        print("No records to write.")
        return
    with open(manifest, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(records[0].keys()))
        writer.writeheader()
        writer.writerows(records)
    print(f"Manifest: {manifest}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate weak metal/artifact NIfTI masks")
    parser.add_argument("--input", required=True, help="DICOM case dir, dir of cases/zips/NIfTI, or zip/NIfTI file")
    parser.add_argument("--output", required=True, help="Output dataset directory")
    parser.add_argument("--mask-mode", choices=["binary", "combined"], default="binary")
    parser.add_argument("--no-previews", action="store_true", help="Do not write preview PNGs")
    args = parser.parse_args()

    input_path = Path(args.input)
    output_dir = Path(args.output)
    cases = [input_path] if input_path.is_file() else iter_cases(input_path)
    records: list[dict[str, object]] = []

    for case in tqdm(cases, desc="Annotating"):
        result = annotate_case(
            case_path=case,
            output_dir=output_dir,
            mask_mode=args.mask_mode,
            save_previews=not args.no_previews,
        )
        records.append(result)
        if result["status"] != "ok":
            tqdm.write(f"[skip] {case}: {result['error']}")

    write_manifest(records, output_dir)
    ok = sum(1 for r in records if r["status"] == "ok")
    print(f"Done: {ok}/{len(records)} cases annotated -> {output_dir}")


if __name__ == "__main__":
    main()
