import os
import tempfile
from pathlib import Path
from typing import Any

import SimpleITK as sitk
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import Response

router = APIRouter(tags=["dicom-conversion"])


@router.post("/dicom2nii")
async def dicom_to_nifti(files: list[UploadFile] = File(...)) -> Response:
    if not files:
        raise HTTPException(status_code=400, detail="No DICOM files were uploaded")

    with tempfile.TemporaryDirectory(prefix="dcm_") as tmpdir:
        saved = []
        for index, upload in enumerate(files):
            name = Path(upload.filename or f"slice_{index}.dcm").name
            target = os.path.join(tmpdir, name)
            content = await upload.read()
            if len(content) < 200:
                continue
            with open(target, "wb") as handle:
                handle.write(content)
            saved.append(target)

        if not saved:
            raise HTTPException(status_code=400, detail="No valid DICOM files were uploaded")

        try:
            image = _read_dicom_series(tmpdir, saved)
            image = _prepare_viewer_volume(image)
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"DICOM parsing failed: {exc}") from exc

        if image.GetDimension() != 3 or image.GetDepth() == 0:
            raise HTTPException(status_code=400, detail="Unable to reconstruct a 3D DICOM volume")

        output = os.path.join(tmpdir, "volume.nii.gz")
        sitk.WriteImage(image, output)
        data = Path(output).read_bytes()
        shape = f"{image.GetDepth()},{image.GetHeight()},{image.GetWidth()}"

    return Response(
        content=data,
        media_type="application/gzip",
        headers={
            "Content-Disposition": "attachment; filename=volume.nii.gz",
            "X-Volume-Shape": shape,
        },
    )


@router.get("/dicom/health")
def dicom_health() -> dict[str, Any]:
    return {
        "status": "ok",
        "sitk": sitk.Version.VersionString(),
    }


def _read_dicom_series(directory: str, saved: list[str]) -> sitk.Image:
    reader = sitk.ImageSeriesReader()
    series_ids = []
    try:
        series_ids = list(reader.GetGDCMSeriesIDs(directory) or [])
    except Exception:
        series_ids = []

    if series_ids:
        reader.SetFileNames(reader.GetGDCMSeriesFileNames(directory, series_ids[0]))
    else:
        reader.SetFileNames(sorted(saved))
    return reader.Execute()


def _prepare_viewer_volume(image: sitk.Image) -> sitk.Image:
    try:
        image = sitk.DICOMOrient(image, "RAI")
    except Exception:
        pass

    spacing = list(image.GetSpacing())
    size = list(image.GetSize())
    if image.GetDimension() != 3 or len(spacing) < 3 or len(size) < 3:
        return image

    inplane = max(float(spacing[0]) or 1.0, float(spacing[1]) or 1.0)
    target_z = min(float(spacing[2]) or 1.0, inplane * 2.0)
    if spacing[2] <= target_z * 1.05:
        return image

    new_spacing = [float(spacing[0]), float(spacing[1]), target_z]
    new_size = [
        int(size[0]),
        int(size[1]),
        max(1, int(round(size[2] * float(spacing[2]) / target_z))),
    ]

    return sitk.Resample(
        image,
        new_size,
        sitk.Transform(),
        sitk.sitkLinear,
        image.GetOrigin(),
        new_spacing,
        image.GetDirection(),
        -1000.0,
        image.GetPixelID(),
    )
