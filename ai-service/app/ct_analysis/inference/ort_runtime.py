"""ONNX Runtime helpers for CT inference."""

from __future__ import annotations

import logging
import os

log = logging.getLogger(__name__)
_preloaded = False


def preload_cuda_dlls(ort) -> None:
    """Load CUDA/cuDNN DLLs from Python packages before creating CUDA sessions."""
    global _preloaded
    if _preloaded:
        return
    _preloaded = True

    enabled = os.getenv("CT_ONNX_PRELOAD_DLLS", "true").lower()
    if enabled in {"0", "false", "no", "off"}:
        return
    if not hasattr(ort, "preload_dlls"):
        return

    directory = os.getenv("CT_ONNX_PRELOAD_DLL_DIRECTORY", "")
    try:
        ort.preload_dlls(directory=directory)
    except Exception as exc:
        log.warning("ONNX Runtime CUDA DLL preload failed: %s", exc)
