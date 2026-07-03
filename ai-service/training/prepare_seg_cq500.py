"""
Prepare Seg-CQ500 for lesion segmentation training.

The script searches a Seg-CQ500 extract directory for CT volumes and hemorrhage
masks, pairs them by filename/directory similarity, and writes a normalized
layout used by train_lesion_segmentation.py:

    lesion_seg_dataset/
      images_nii/
        case_0001.nii.gz
      labels_nii/
        case_0001.nii.gz
      manifest.csv

Usage:
    python ai-service/training/prepare_seg_cq500.py \
      --input /root/autodl-tmp/Seg-CQ500 \
      --output /root/autodl-tmp/lesion_seg_dataset
"""

from __future__ import annotations

import argparse
import csv
import re
import shutil
from difflib import SequenceMatcher
from pathlib import Path

import numpy as np
import SimpleITK as sitk
from tqdm import tqdm


IMAGE_DIR_HINTS = {"image", "images", "img", "imgs", "ct", "scan", "scans", "volume", "volumes"}
MASK_DIR_HINTS = {
    "label",
    "labels",
    "mask",
    "masks",
    "segs",
    "segmentation",
    "segmentations",
    "annotation",
    "annotations",
    "gt",
    "groundtruth",
    "hemorrhage",
    "ich",
}
MASK_NAME_HINTS = MASK_DIR_HINTS | {"blood", "lesion", "target", "manual", "seg"}
IMAGE_NAME_HINTS = IMAGE_DIR_HINTS | {"head", "brain", "ncct", "noncontrast"}
VALID_SUFFIXES = (".nii", ".nii.gz", ".mha", ".mhd", ".nrrd")


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare Seg-CQ500 image/mask pairs")
    parser.add_argument("--input", required=True, help="Extracted Seg-CQ500 directory")
    parser.add_argument("--output", required=True, help="Output dataset directory")
    parser.add_argument("--limit", type=int, default=0, help="Optional max cases for a dry run")
    parser.add_argument("--copy", action="store_true", help="Copy original files instead of rewriting NIfTI")
    parser.add_argument("--keep-label-values", action="store_true", help="Keep mask values instead of binarizing >0")
    args = parser.parse_args()

    input_dir = Path(args.input)
    output_dir = Path(args.output)
    images_dir = output_dir / "images_nii"
    labels_dir = output_dir / "labels_nii"
    images_dir.mkdir(parents=True, exist_ok=True)
    labels_dir.mkdir(parents=True, exist_ok=True)

    files = _find_volume_files(input_dir)
    if not files:
        raise FileNotFoundError(f"No NIfTI/MHA/NRRD files found under {input_dir}")

    images, masks = _split_candidates(files)
    print(f"Found candidates: images={len(images)} masks={len(masks)}")
    if not images or not masks:
        raise RuntimeError("Could not identify both image and mask files. Check the extracted directory structure.")

    pairs = _pair_files(images, masks)
    if args.limit > 0:
        pairs = pairs[: args.limit]
    if not pairs:
        raise RuntimeError("No image/mask pairs found.")

    rows = []
    prepared_count = 0
    skipped_rows = []
    for candidate_idx, (image_path, mask_path, score) in enumerate(tqdm(pairs, desc="Preparing pairs"), start=1):
        case_id = f"segcq500_tmp_{candidate_idx:04d}"
        image_out = images_dir / f"{case_id}.nii.gz"
        mask_out = labels_dir / f"{case_id}.nii.gz"

        try:
            if args.copy and image_path.suffixes[-2:] == [".nii", ".gz"] and mask_path.suffixes[-2:] == [".nii", ".gz"]:
                shutil.copy2(image_path, image_out)
                shutil.copy2(mask_path, mask_out)
                image_shape = _read_shape(image_out)
                mask_shape = _read_shape(mask_out)
                mask_voxels = _count_mask_voxels(mask_out)
            else:
                image_shape, mask_shape, mask_voxels = _rewrite_pair(
                    image_path,
                    mask_path,
                    image_out,
                    mask_out,
                    keep_label_values=args.keep_label_values,
                )
        except Exception as exc:
            skipped_rows.append(
                {
                    "source_image": str(image_path),
                    "source_mask": str(mask_path),
                    "pair_score": f"{score:.4f}",
                    "reason": str(exc),
                }
            )
            continue

        prepared_count += 1
        case_id = f"segcq500_{prepared_count:04d}"
        image_out_final = images_dir / f"{case_id}.nii.gz"
        mask_out_final = labels_dir / f"{case_id}.nii.gz"
        if image_out != image_out_final:
            image_out.replace(image_out_final)
            mask_out.replace(mask_out_final)
            image_out = image_out_final
            mask_out = mask_out_final

        rows.append(
            {
                "case_id": case_id,
                "image_path": str(image_out),
                "mask_path": str(mask_out),
                "source_image": str(image_path),
                "source_mask": str(mask_path),
                "pair_score": f"{score:.4f}",
                "image_shape": "x".join(map(str, image_shape)),
                "mask_shape": "x".join(map(str, mask_shape)),
                "mask_voxels": int(mask_voxels) if "mask_voxels" in locals() else "",
            }
        )

    if skipped_rows:
        _write_manifest(output_dir / "skipped_pairs.csv", skipped_rows)
        print(f"Skipped {len(skipped_rows)} invalid pairs. See skipped_pairs.csv")
    if not rows:
        raise RuntimeError("No valid image/mask pairs could be prepared. See skipped_pairs.csv if present.")

    _write_manifest(output_dir / "manifest.csv", rows)
    print(f"Prepared {len(rows)} cases -> {output_dir}")
    print("Next: train_lesion_segmentation.py --data", output_dir)


def _find_volume_files(root: Path) -> list[Path]:
    result = []
    for path in root.rglob("*"):
        if _is_junk_file(path):
            continue
        if path.is_file() and _has_valid_suffix(path):
            result.append(path)
    return sorted(result)


def _is_junk_file(path: Path) -> bool:
    parts = {part.lower() for part in path.parts}
    return "__macosx" in parts or path.name.startswith("._")


def _has_valid_suffix(path: Path) -> bool:
    name = path.name.lower()
    return any(name.endswith(suffix) for suffix in VALID_SUFFIXES)


def _split_candidates(files: list[Path]) -> tuple[list[Path], list[Path]]:
    images: list[Path] = []
    masks: list[Path] = []
    unknown: list[Path] = []

    for path in files:
        parent_tokens = _tokens(path.parent.name.lower())
        name_tokens = _tokens(path.name.lower())
        mask_score = len((parent_tokens | name_tokens) & MASK_NAME_HINTS)
        image_score = len((parent_tokens | name_tokens) & IMAGE_NAME_HINTS)

        if mask_score > image_score:
            masks.append(path)
        elif image_score > mask_score:
            images.append(path)
        else:
            unknown.append(path)

    # nnU-Net style directories are common enough to deserve a deterministic pass.
    for path in unknown:
        lower_parts = {part.lower() for part in path.parts}
        if lower_parts & MASK_DIR_HINTS:
            masks.append(path)
        elif lower_parts & IMAGE_DIR_HINTS:
            images.append(path)

    return sorted(set(images)), sorted(set(masks))


def _pair_files(images: list[Path], masks: list[Path]) -> list[tuple[Path, Path, float]]:
    available_images = set(images)
    pairs: list[tuple[Path, Path, float]] = []
    image_sizes = {path: _read_size(path) for path in tqdm(images, desc="Reading image headers")}
    mask_sizes = {path: _read_size(path) for path in tqdm(masks, desc="Reading mask headers")}

    for mask_path in sorted(masks):
        ranked: list[tuple[float, Path]] = []
        mask_key = _case_key(mask_path)
        mask_size = mask_sizes.get(mask_path)
        for image_path in available_images:
            image_key = _case_key(image_path)
            score = _pair_score(image_path, image_key, mask_path, mask_key)
            if mask_size and image_sizes.get(image_path) == mask_size:
                score += 1.0
            ranked.append((score, image_path))

        best_image = None
        best_score = -1.0
        exact_dir = [path for path in available_images if path.parent == mask_path.parent]
        if exact_dir:
            best_image = _select_ct_image(exact_dir)
            best_score = 10.0
        for score, image_path in sorted(ranked, key=lambda item: item[0], reverse=True):
            if best_image is not None:
                break
            if mask_size and image_sizes.get(image_path) != mask_size:
                continue
            best_image = image_path
            best_score = score
            break
        if best_image is None and ranked:
            best_score, best_image = max(ranked, key=lambda item: item[0])

        if best_image is not None and best_score >= 0.20:
            available_images.remove(best_image)
            pairs.append((best_image, mask_path, best_score))

    if not pairs and images and masks:
        print("Name-based pairing failed; falling back to sorted same-shape pairing.")
        available_images = set(images)
        for mask_path in sorted(masks):
            mask_size = mask_sizes.get(mask_path)
            same_shape = [path for path in sorted(available_images) if image_sizes.get(path) == mask_size]
            if not same_shape:
                continue
            image_path = same_shape[0]
            available_images.remove(image_path)
            pairs.append((image_path, mask_path, 1.0))

    return sorted(pairs, key=lambda item: item[0].name)


def _pair_score(image_path: Path, image_key: str, mask_path: Path, mask_key: str) -> float:
    score = SequenceMatcher(None, image_key, mask_key).ratio()
    if image_key == mask_key:
        score += 0.5
    if image_key and mask_key and (image_key in mask_key or mask_key in image_key):
        score += 0.2
    common_dirs = set(_tokens(str(image_path.parent))) & set(_tokens(str(mask_path.parent)))
    score += min(len(common_dirs) * 0.02, 0.12)
    return score


def _select_ct_image(paths: list[Path]) -> Path:
    def priority(path: Path) -> tuple[int, str]:
        name = path.name.lower()
        if name in {"ct.nii", "ct.nii.gz"}:
            return (0, name)
        if name.startswith("ct.") or name.startswith("ct_"):
            return (1, name)
        if "ct" in name:
            return (2, name)
        return (3, name)

    return sorted(paths, key=priority)[0]


def _case_key(path: Path) -> str:
    stem = path.name.lower()
    stem = re.sub(r"\.nii\.gz$|\.nii$|\.mha$|\.mhd$|\.nrrd$", "", stem)
    stem = re.sub(r"_0000$", "", stem)
    tokens = [tok for tok in _tokens(stem) if tok not in MASK_NAME_HINTS and tok not in IMAGE_NAME_HINTS]
    if not tokens:
        tokens = _tokens(stem)
    return "_".join(tokens)


def _tokens(text: str) -> set[str]:
    return {tok for tok in re.split(r"[^a-zA-Z0-9]+", text.lower()) if tok}


def _rewrite_pair(
    image_path: Path,
    mask_path: Path,
    image_out: Path,
    mask_out: Path,
    keep_label_values: bool,
) -> tuple[tuple[int, ...], tuple[int, ...], int]:
    image, image_arr = _read_volume_array(image_path, dtype=np.float32)
    mask, mask_arr = _read_volume_array(mask_path)

    if image_arr.shape != mask_arr.shape:
        if image is not None and mask is not None:
            mask = _resample_mask_to_image(mask, image)
            mask_arr = sitk.GetArrayFromImage(mask)
        if image_arr.shape != mask_arr.shape:
            raise ValueError(f"Shape mismatch after resample: {image_path} {image_arr.shape} vs {mask_path} {mask_arr.shape}")

    if keep_label_values:
        label_arr = mask_arr.astype(np.uint8)
    else:
        label_arr = (mask_arr > 0).astype(np.uint8)

    image_out_img = sitk.GetImageFromArray(image_arr)
    if image is not None:
        _safe_copy_information(image_out_img, image)
    mask_out_img = sitk.GetImageFromArray(label_arr)
    if image is not None:
        _safe_copy_information(mask_out_img, image)
    sitk.WriteImage(image_out_img, str(image_out))
    sitk.WriteImage(mask_out_img, str(mask_out))
    return image_arr.shape, label_arr.shape, int(label_arr.sum())


def _resample_mask_to_image(mask: sitk.Image, image: sitk.Image) -> sitk.Image:
    return sitk.Resample(
        mask,
        image,
        sitk.Transform(),
        sitk.sitkNearestNeighbor,
        0,
        sitk.sitkUInt8,
    )


def _read_shape(path: Path) -> tuple[int, ...]:
    _, array = _read_volume_array(path)
    return tuple(array.shape)


def _read_size(path: Path) -> tuple[int, ...] | None:
    try:
        return tuple(sitk.ReadImage(str(path)).GetSize())
    except Exception:
        return None


def _count_mask_voxels(path: Path) -> int:
    _, array = _read_volume_array(path)
    return int((array > 0).sum())


def _read_volume_array(path: Path, dtype=None) -> tuple[sitk.Image | None, np.ndarray]:
    try:
        image = sitk.ReadImage(str(path))
        array = sitk.GetArrayFromImage(image)
        if dtype is not None:
            array = array.astype(dtype)
        return image, array
    except Exception as sitk_exc:
        try:
            import nibabel as nib
        except ImportError as exc:
            raise RuntimeError(
                f"SimpleITK could not read {path}; install nibabel for fallback reading. "
                f"Original error: {sitk_exc}"
            ) from exc

        nii = nib.load(str(path))
        array = np.asanyarray(nii.dataobj)
        if array.ndim == 3:
            array = np.transpose(array, (2, 1, 0))
        elif array.ndim == 4 and array.shape[-1] == 1:
            array = np.transpose(array[..., 0], (2, 1, 0))
        if dtype is not None:
            array = array.astype(dtype)
        return None, np.ascontiguousarray(array)


def _safe_copy_information(target: sitk.Image, source: sitk.Image) -> None:
    try:
        if target.GetDimension() == source.GetDimension() and target.GetSize() == source.GetSize():
            target.CopyInformation(source)
    except Exception:
        pass


def _write_manifest(path: Path, rows: list[dict]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
