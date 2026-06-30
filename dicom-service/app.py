"""
医学影像 AI 微服务
  /dicom2nii  — DICOM → NIfTI 格式转换
  /ct-analyze — 颅内出血分类（基于 classifier.onnx）
  /health     — 健康检查

依赖: pip install fastapi "uvicorn[standard]" python-multipart SimpleITK onnxruntime pillow
启动: uvicorn app:app --host 0.0.0.0 --port 8765 --reload
"""
import logging
import os
import tempfile
from pathlib import Path
from typing import List

import numpy as np
import SimpleITK as sitk
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, JSONResponse

sitk.ProcessObject_SetGlobalWarningDisplay(False)
logging.basicConfig(level=logging.INFO)

app = FastAPI(title="医学影像 AI 微服务", version="1.1")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # 生产环境改为具体前端域
    allow_methods=["POST", "GET", "OPTIONS"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    from pathlib import Path
    model_ready = (Path(__file__).parent / "models" / "classifier.onnx").exists()
    return {
        "status": "ok",
        "sitk": sitk.Version.VersionString(),
        "model_ready": model_ready,
    }


def prepare_viewer_volume(image: sitk.Image) -> sitk.Image:
    """Orient and lightly resample CT volume for browser MPR/VR viewing.

    Mirrors the local SimpleITK/VTK tools: SimpleITK handles DICOM decoding,
    direction is normalized, and thick-slice series get linear Z interpolation.
    X/Y spacing is preserved; only Z is reduced when it is much thicker than
    in-plane pixels, keeping files reasonable while improving coronal/sagittal
    and 3D browser rendering.
    """
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


# ─── 颅内出血分类接口 ─────────────────────────────────────────────────────────

def _load_volume_from_uploads(files: List[UploadFile], tmpdir: str) -> np.ndarray:
    """将上传的 DICOM/NIfTI 文件解析为 numpy volume (nz, ny, nx)，单位 HU。"""
    saved = []
    for uf in files:
        name = Path(uf.filename or "slice").name if uf.filename else f"s_{len(saved)}"
        dest = os.path.join(tmpdir, name)
        # 注意：此函数在 async 上下文外调用，文件已由调用方 await 读取
        saved.append(dest)

    reader = sitk.ImageSeriesReader()
    try:
        ids = reader.GetGDCMSeriesIDs(tmpdir)
    except Exception:
        ids = []

    if ids:
        reader.SetFileNames(reader.GetGDCMSeriesFileNames(tmpdir, ids[0]))
    else:
        reader.SetFileNames(sorted(saved))

    image = reader.Execute()
    vol = sitk.GetArrayFromImage(image).astype(np.float32)  # (nz, ny, nx)
    if vol.ndim == 2:
        vol = vol[np.newaxis]
    return vol


@app.post("/ct-analyze")
async def ct_analyze(files: List[UploadFile] = File(...)):
    """
    接收 DICOM 序列（与 /dicom2nii 相同的文件集），
    返回颅内出血分类结果（JSON）。

    响应示例：
    {
      "label": "hemorrhage",
      "label_cn": "疑似颅内出血",
      "confidence": 0.923,
      "affected_slices": [5, 6, 7],
      "slice_count": 30,
      "findings": "AI 辅助分析提示：…",
      "conclusion": "疑似颅内出血，建议临床复查。"
    }
    """
    if not files:
        raise HTTPException(422, "未收到任何文件")

    # 检查模型是否就绪
    from inference import HemorrhageClassifier
    try:
        clf = HemorrhageClassifier.get()
    except FileNotFoundError as e:
        raise HTTPException(503, str(e))

    # 保存上传文件 → 解析体积
    with tempfile.TemporaryDirectory(prefix="ai_") as tmpdir:
        for uf in files:
            name = Path(uf.filename or "slice").name if uf.filename else f"s_{uf.filename}"
            dest = os.path.join(tmpdir, name)
            content = await uf.read()
            if len(content) >= 200:
                with open(dest, "wb") as fp:
                    fp.write(content)

        try:
            vol = _load_volume_from_uploads(files, tmpdir)
        except Exception as exc:
            raise HTTPException(422, f"CT 解析失败: {exc}")

    # ── 分类推理（ONNX，快速）───────────────────────────────────────────────
    result = clf.predict(vol)

    # ── GradCAM 热力图（PyTorch，仅对阳性结果计算）────────────────────────
    gradcam_slices: list[dict] = []
    if result.label == "hemorrhage":
        try:
            from inference import GradCAMVisualizer
            cam_vis = GradCAMVisualizer.get()
            gradcam_slices = cam_vis.compute_volume(vol, top_k=3)
        except FileNotFoundError:
            pass   # best_classifier.pt 未部署时静默跳过
        except Exception as exc:
            logging.getLogger(__name__).warning("GradCAM 失败: %s", exc)

    return JSONResponse({
        "label":           result.label,
        "label_cn":        result.label_cn,
        "confidence":      result.confidence,
        "affected_slices": result.affected_slices,
        "slice_count":     len(result.slice_probs),
        "slice_probs":     result.slice_probs,
        "findings":        result.to_report_context(),
        "conclusion":      (
            "疑似颅内出血，建议结合临床症状及 MRI 进一步检查，必要时神经外科会诊。"
            if result.label == "hemorrhage"
            else "未见明确颅内出血征象，请结合临床综合判断。"
        ),
        # GradCAM 结果（仅出血阳性时存在）
        # 每个元素: {"slice_idx": int, "prob": float, "heatmap_b64": str, "bbox_yolo": str}
        "gradcam_slices":  gradcam_slices,
    })


@app.post("/dicom2nii")
async def dicom_to_nifti(files: List[UploadFile] = File(...)) -> Response:
    """
    接收一组 DICOM 文件，返回 NIfTI (.nii.gz) 体积数据。
    支持所有传输语法（未压缩 / JPEG Lossless / JPEG2000 / RLE 等），
    因为底层使用 SimpleITK / GDCM 解码。
    """
    if not files:
        raise HTTPException(422, "未收到任何文件")

    with tempfile.TemporaryDirectory(prefix="dcm_") as tmpdir:
        # ── 1. 保存上传文件 ───────────────────────────────────────────
        saved = []
        for uf in files:
            name = Path(uf.filename or "slice").name if uf.filename else f"slice_{len(saved)}"
            dest = os.path.join(tmpdir, name)
            content = await uf.read()
            if len(content) < 200:
                continue          # 太小，跳过
            with open(dest, "wb") as fp:
                fp.write(content)
            saved.append(dest)

        if not saved:
            raise HTTPException(422, "没有有效文件")

        # ── 2. 用 SimpleITK 读取 DICOM 序列 ──────────────────────────
        reader = sitk.ImageSeriesReader()
        series_ids = []
        try:
            series_ids = reader.GetGDCMSeriesIDs(tmpdir)
        except Exception:
            pass

        if series_ids:
            fns = reader.GetGDCMSeriesFileNames(tmpdir, series_ids[0])
            reader.SetFileNames(fns)
        else:
            # 无 SeriesInstanceUID → 按文件名排序后直接读
            reader.SetFileNames(sorted(saved))

        try:
            image = reader.Execute()
        except Exception as exc:
            raise HTTPException(422, f"DICOM 解析失败: {exc}")

        image = prepare_viewer_volume(image)
        nz, ny, nx = image.GetDepth(), image.GetHeight(), image.GetWidth()
        if nz == 0:
            raise HTTPException(422, "未能重建三维体积（可能只有单张切片）")

        # ── 3. 写出 NIfTI 并返回 ─────────────────────────────────────
        out = os.path.join(tmpdir, "volume.nii.gz")
        sitk.WriteImage(image, out)
        data = Path(out).read_bytes()

    return Response(
        content=data,
        media_type="application/gzip",
        headers={
            "Content-Disposition": "attachment; filename=volume.nii.gz",
            "X-Volume-Shape": f"{nz},{ny},{nx}",
        },
    )
