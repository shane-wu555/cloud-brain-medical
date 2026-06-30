"""
颅内出血检测推理模块
模型：classifier.onnx（ONNX Runtime 推理）
输入：CT 切片序列（numpy array，shape = [nz, ny, nx]，HU 单位）
输出：{ label, label_cn, confidence, affected_slices, slice_probs }
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)

MODEL_DIR  = Path(__file__).parent / "models"
ONNX_PATH  = MODEL_DIR / "classifier.onnx"

# ── 3通道窗口（与 training/preprocess_cq500.py apply_windows() 完全一致）──
# channel-0: 脑窗   WW=80,   WL=40
# channel-1: 血窗   WW=175,  WL=75
# channel-2: 颅骨   WW=2500, WL=480
WINDOWS = [
    (80,   40),    # (width, level)
    (175,  75),
    (2500, 480),
]

# ── 推理阈值 ──────────────────────────────────────────────────────────────
SLICE_THRESHOLD  = 0.50   # 单张切片：P(出血) > 此值 → 该切片标记为阳性
VOLUME_THRESHOLD = 0.50   # 全卷：最大切片概率 > 此值 → 整体判断为出血


@dataclass
class HemorrhageResult:
    label: str            # "hemorrhage" | "normal"
    label_cn: str         # "疑似颅内出血" | "未检测到出血"
    confidence: float     # 0.0 ~ 1.0，取所有切片最大概率
    affected_slices: list[int] = field(default_factory=list)
    slice_probs: list[float]   = field(default_factory=list)
    model_input_size: tuple    = (224, 224)

    def to_report_context(self) -> str:
        """生成可直接插入 AI 报告草稿的文字。"""
        if self.label == "hemorrhage":
            sl = ", ".join(str(s + 1) for s in self.affected_slices[:5])
            return (
                f"AI 辅助分析提示：颅内出血风险（置信度 {self.confidence:.1%}），"
                f"阳性切片：第 {sl} 层（共 {len(self.affected_slices)} 层）。"
                f"建议结合临床及影像学表现综合判断。"
            )
        return (
            f"AI 辅助分析提示：未检测到颅内出血信号（置信度 {self.confidence:.1%}）。"
        )


class HemorrhageClassifier:
    """ONNX Runtime 颅内出血分类器（延迟加载，线程安全）。"""

    _instance: Optional["HemorrhageClassifier"] = None

    def __init__(self):
        import onnxruntime as ort
        if not ONNX_PATH.exists():
            raise FileNotFoundError(
                f"模型文件未找到：{ONNX_PATH}\n"
                f"请将 classifier.onnx 放到 {MODEL_DIR}/ 目录下"
            )
        self.session = ort.InferenceSession(
            str(ONNX_PATH),
            providers=["CUDAExecutionProvider", "CPUExecutionProvider"],
        )
        inp = self.session.get_inputs()[0]
        self.input_name  = inp.name
        self.input_shape = inp.shape                    # e.g. [1, 1, 224, 224]
        self.target_h    = int(self.input_shape[2]) if len(self.input_shape) >= 4 else 224
        self.target_w    = int(self.input_shape[3]) if len(self.input_shape) >= 4 else 224
        self.n_channels  = int(self.input_shape[1]) if len(self.input_shape) >= 2 else 1

        out_shape = self.session.get_outputs()[0].shape
        # output shape: [1, 2] → softmax two-class  |  [1, 1] → sigmoid
        self.use_softmax = (len(out_shape) >= 2 and out_shape[-1] == 2)
        logger.info(
            "ONNX 模型加载完成  input=%s  output=%s  mode=%s",
            self.input_shape, out_shape,
            "softmax" if self.use_softmax else "sigmoid",
        )

    # ── 预处理单张切片 ──────────────────────────────────────────────────────
    def _preprocess(self, slice_hu: np.ndarray) -> np.ndarray:
        """
        HU 切片 → 模型输入 tensor [1, 3, H, W]。
        与 training/preprocess_cq500.py apply_windows() 完全一致：
          ch0 脑窗 WW=80  WL=40
          ch1 血窗 WW=175 WL=75
          ch2 颅骨 WW=2500 WL=480
        再经 MONAI NormalizeIntensity（channel_wise, nonzero）。
        """
        from PIL import Image

        def window_channel(hu: np.ndarray, ww: float, wl: float) -> np.ndarray:
            lo, hi = wl - ww / 2, wl + ww / 2
            return np.clip((hu - lo) / (hi - lo), 0.0, 1.0).astype(np.float32)

        hu = slice_hu.astype(np.float32)
        channels = [window_channel(hu, ww, wl) for ww, wl in WINDOWS]   # 3 × [H, W]
        arr = np.stack(channels, axis=0)   # [3, H, W]

        # Resize 每个通道到目标尺寸
        resized = []
        for ch in arr:
            pil = Image.fromarray((ch * 255).astype(np.uint8))
            pil = pil.resize((self.target_w, self.target_h), Image.BILINEAR)
            resized.append(np.asarray(pil, dtype=np.float32) / 255.0)
        arr = np.stack(resized, axis=0)    # [3, H, W]

        # MONAI NormalizeIntensity(nonzero=True, channel_wise=True)
        for i in range(arr.shape[0]):
            mask = arr[i] > 0
            if mask.any():
                m, s = arr[i][mask].mean(), arr[i][mask].std()
                arr[i] = (arr[i] - m) / (s + 1e-8)

        return arr[np.newaxis, ...].astype(np.float32)   # [1, 3, H, W]

    # ── 单张切片推理 ────────────────────────────────────────────────────────
    def _predict_slice(self, slice_hu: np.ndarray) -> float:
        """返回出血概率（0-1）。"""
        inp = self._preprocess(slice_hu)
        raw = self.session.run(None, {self.input_name: inp})[0]   # [1, 2] or [1, 1]
        if self.use_softmax:
            return float(raw[0][1])                    # P(class=1 = 出血)
        return float(raw[0][0])                        # sigmoid output

    # ── 全卷推理 ────────────────────────────────────────────────────────────
    def predict(self, volume: np.ndarray) -> HemorrhageResult:
        """
        volume: numpy array，shape (nz, ny, nx)，HU 单位。
        """
        nz = volume.shape[0]
        slice_probs: list[float] = []

        for z in range(nz):
            try:
                p = self._predict_slice(volume[z])
            except Exception as exc:
                logger.warning("切片 %d 推理失败: %s", z, exc)
                p = 0.0
            slice_probs.append(round(p, 4))

        max_prob       = max(slice_probs) if slice_probs else 0.0
        affected       = [i for i, p in enumerate(slice_probs) if p >= SLICE_THRESHOLD]
        label          = "hemorrhage" if max_prob >= VOLUME_THRESHOLD else "normal"
        label_cn       = "疑似颅内出血" if label == "hemorrhage" else "未检测到出血"

        return HemorrhageResult(
            label          = label,
            label_cn       = label_cn,
            confidence     = round(max_prob, 4),
            affected_slices= affected,
            slice_probs    = slice_probs,
            model_input_size=(self.target_h, self.target_w),
        )

    # ── 单例获取 ────────────────────────────────────────────────────────────
    @classmethod
    def get(cls) -> "HemorrhageClassifier":
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance


# ─── GradCAM 可视化（需要 best_classifier.pt + PyTorch + timm）────────────────
# ONNX 推理用于快速分类，PyTorch 用于梯度反传生成热力图。

PT_PATH = MODEL_DIR / "best_classifier.pt"


class GradCAMVisualizer:
    """
    基于 best_classifier.pt（PyTorch）的 GradCAM 热力图生成器。
    对最可疑的切片输出一张 RGBA 红色叠加热力图（base64 PNG）。

    使用前提：best_classifier.pt 与 classifier.onnx 放在同一 models/ 目录。
    """

    _instance: Optional["GradCAMVisualizer"] = None

    def __init__(self):
        import timm
        import torch

        if not PT_PATH.exists():
            raise FileNotFoundError(
                f"PyTorch 权重未找到：{PT_PATH}\n"
                f"GradCAM 需要 best_classifier.pt，请从训练输出目录复制过来。"
            )

        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model  = timm.create_model(
            "efficientnet_b3", pretrained=False, num_classes=2
        )
        self.model.load_state_dict(
            torch.load(str(PT_PATH), map_location=self.device)
        )
        self.model.to(self.device).eval()

        # Target layer：EfficientNet-B3 最后一个卷积（global pooling 之前）
        # conv_head: (B, 1536, H', W') → 对应特征图大小约 16×16（输入512时）
        self._target = self.model.conv_head
        self._acts: Optional[np.ndarray] = None
        self._grads: Optional[np.ndarray] = None

        self._fwd = self._target.register_forward_hook(self._hook_act)
        self._bwd = self._target.register_full_backward_hook(self._hook_grad)

        logger.info("GradCAM visualizer ready  device=%s", self.device)

    # ── Hooks ───────────────────────────────────────────────────────────────
    def _hook_act(self, m, inp, out):
        self._acts = out.detach().cpu().numpy()        # (1, C, H', W')

    def _hook_grad(self, m, grad_in, grad_out):
        self._grads = grad_out[0].detach().cpu().numpy()   # (1, C, H', W')

    # ── 公开接口 ────────────────────────────────────────────────────────────
    def compute(self, slice_hu: np.ndarray,
                target_class: int = 1) -> tuple[np.ndarray, float]:
        """
        对单张切片计算 GradCAM。
        返回 (heatmap [H, W] 归一化到 [0,1]，该切片出血概率)。
        """
        import torch
        import cv2

        inp = self._preprocess(slice_hu).to(self.device)
        inp.requires_grad_(True)

        self.model.zero_grad()
        logits = self.model(inp)                    # (1, 2)
        prob   = float(torch.softmax(logits, 1)[0, target_class])

        logits[0, target_class].backward()

        weights = self._grads.mean(axis=(2, 3), keepdims=True)   # (1, C, 1, 1)
        cam     = (weights * self._acts).sum(axis=1).squeeze()    # (H', W')
        cam     = np.maximum(cam, 0)                              # ReLU

        # 上采样至输入分辨率（512×512）
        cam = cv2.resize(cam.astype(np.float32),
                         (inp.shape[3], inp.shape[2]),
                         interpolation=cv2.INTER_LINEAR)
        if cam.max() > 0:
            cam = cam / cam.max()

        return cam, prob

    def compute_volume(self, volume: np.ndarray,
                       top_k: int = 3) -> list[dict]:
        """
        对整个体积，找出最可疑的 top_k 张切片，各返回一张热力图。
        返回列表：[{"slice_idx": z, "prob": 0.9, "heatmap_b64": "...", "bbox_yolo": "0 ..."}]
        """
        nz = volume.shape[0]
        # 先用 ONNX 快速筛选最可疑切片（避免对所有切片跑 PyTorch 反传）
        try:
            clf = HemorrhageClassifier.get()
            probs = [clf._predict_slice(volume[z]) for z in range(nz)]
        except Exception:
            # 若 ONNX 不可用，直接用 PyTorch 筛选
            probs = [self.compute(volume[z])[1] for z in range(nz)]

        # 取概率最高的 top_k 张
        ranked = sorted(range(nz), key=lambda i: probs[i], reverse=True)
        top_slices = [z for z in ranked[:top_k] if probs[z] >= SLICE_THRESHOLD]

        results = []
        for z in top_slices:
            cam, prob = self.compute(volume[z])
            results.append({
                "slice_idx":   z,
                "prob":        round(prob, 4),
                "heatmap_b64": heatmap_to_base64(cam),
                "bbox_yolo":   heatmap_to_yolo_bbox(cam),
            })

        return results

    def _preprocess(self, slice_hu: np.ndarray):
        """与 HemorrhageClassifier._preprocess 保持一致。"""
        import torch
        from PIL import Image

        def win_ch(hu, ww, wl):
            lo, hi = wl - ww / 2, wl + ww / 2
            return np.clip((hu - lo) / (hi - lo), 0, 1).astype(np.float32)

        hu  = slice_hu.astype(np.float32)
        arr = np.stack([win_ch(hu, ww, wl) for ww, wl in WINDOWS])  # (3, H, W)

        resized = []
        for ch in arr:
            pil = Image.fromarray((ch * 255).astype(np.uint8))
            pil = pil.resize((512, 512), Image.BILINEAR)
            resized.append(np.asarray(pil, dtype=np.float32) / 255.0)
        arr = np.stack(resized)  # (3, 512, 512)

        for i in range(3):
            m = arr[i][arr[i] > 0]
            if m.size:
                arr[i] = (arr[i] - m.mean()) / (m.std() + 1e-8)

        return torch.from_numpy(arr[np.newaxis]).float()   # (1, 3, 512, 512)

    def destroy(self):
        self._fwd.remove()
        self._bwd.remove()

    @classmethod
    def get(cls) -> "GradCAMVisualizer":
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance


# ─── 热力图工具函数 ────────────────────────────────────────────────────────────

def heatmap_to_base64(heatmap: np.ndarray) -> str:
    """
    热力图 (H, W) [0,1] → base64 RGBA PNG（红色渐变叠加层）。
    前端直接用 <img src="data:image/png;base64,..."> 叠加显示。
    """
    import base64, io
    from PIL import Image

    h, w = heatmap.shape
    rgba = np.zeros((h, w, 4), dtype=np.uint8)
    rgba[:, :, 0] = 255                                  # R 固定满
    rgba[:, :, 1] = (255 * (1 - heatmap)).astype(np.uint8)  # G 反向 → 越热越红
    rgba[:, :, 2] = 0
    rgba[:, :, 3] = (heatmap * 200).astype(np.uint8)    # alpha，最大 200/255

    buf = io.BytesIO()
    Image.fromarray(rgba, "RGBA").save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


def heatmap_to_yolo_bbox(heatmap: np.ndarray,
                          threshold: float = 0.4,
                          class_id: int = 0) -> Optional[str]:
    """
    热力图 → YOLO 格式候选框字符串（归一化坐标）。
    class_id=0=hemorrhage，与 train_detector.py 的 CLASSES 对应。
    返回 None 表示热力图中没有有效区域。
    """
    import cv2

    binary   = (heatmap > threshold).astype(np.uint8)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None

    pts = np.vstack(contours)
    x, y, bw, bh = cv2.boundingRect(pts)
    H, W = heatmap.shape
    xc = (x + bw / 2) / W
    yc = (y + bh / 2) / H
    return f"{class_id} {xc:.6f} {yc:.6f} {bw/W:.6f} {bh/H:.6f}"
