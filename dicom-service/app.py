"""
DICOM → NIfTI 转换微服务
依赖: pip install fastapi "uvicorn[standard]" python-multipart SimpleITK
启动: uvicorn app:app --host 0.0.0.0 --port 8765 --reload
"""
import os
import tempfile
from pathlib import Path
from typing import List

import SimpleITK as sitk
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

sitk.ProcessObject_SetGlobalWarningDisplay(False)

app = FastAPI(title="DICOM Conversion Service", version="1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # 生产环境改为具体前端域
    allow_methods=["POST", "GET", "OPTIONS"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok", "sitk": sitk.Version.VersionString()}


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
