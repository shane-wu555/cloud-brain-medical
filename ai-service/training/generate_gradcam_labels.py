"""
用 GradCAM 为 CQ500 出血切片生成 YOLO 候选框伪标签
============================================================
输入：
  --data   cq500_processed/  (preprocess_cq500.py 的输出)
  --model  models/best_classifier.pt
输出：
  cq500_processed/gradcam_labels/<slice_name>.txt  (YOLO 格式)
  cq500_processed/gradcam_preview/<slice_name>.png (叠加热力图预览，可选)

生成完毕后人工抽检 gradcam_preview/ 目录里的图片，
修正明显错误的 bbox，然后用这批标注跑 train_detector.py。

用法:
    python training/generate_gradcam_labels.py \\
        --data   /data/cq500_processed \\
        --model  /models/metal_binary/best_classifier.pt \\
        --preview        # 同时保存叠加预览图
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

import cv2
import numpy as np

# 与 preprocess_cq500.py 完全相同的三通道窗口
WINDOWS = [(80, 40), (175, 75), (2500, 480)]


def build_input_tensor(png_path: Path):
    """
    读取已处理的 PNG → MONAI NormalizeIntensity 预处理 → [1,3,512,512] tensor
    （与 train_classifier.py 的 get_transforms 完全一致）
    """
    import torch
    img = cv2.imread(str(png_path))     # BGR
    if img is None:
        return None
    # PNG 通道顺序（cv2 读为 BGR）:  B=bone, G=blood, R=brain
    # 转为 [brain, blood, bone]（即 RGB）
    rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    arr = rgb.transpose(2, 0, 1)       # (3, H, W)
    for i in range(3):
        m = arr[i][arr[i] > 0]
        if m.size:
            arr[i] = (arr[i] - m.mean()) / (m.std() + 1e-8)
    return torch.from_numpy(arr[np.newaxis]).float()  # (1,3,512,512)


def compute_gradcam(model, inp, device, target_class: int = 1):
    """手动计算 GradCAM，不依赖外部库。"""
    import torch

    acts, grads = {}, {}

    def fwd_hook(m, i, o):
        acts["v"] = o.detach().cpu().numpy()

    def bwd_hook(m, gi, go):
        grads["v"] = go[0].detach().cpu().numpy()

    target = model.conv_head
    h1 = target.register_forward_hook(fwd_hook)
    h2 = target.register_full_backward_hook(bwd_hook)

    inp = inp.to(device).requires_grad_(True)
    model.zero_grad()
    logits = model(inp)
    prob   = float(torch.softmax(logits, 1)[0, target_class])
    logits[0, target_class].backward()

    h1.remove(); h2.remove()

    w   = grads["v"].mean(axis=(2, 3), keepdims=True)  # (1,C,1,1)
    cam = (w * acts["v"]).sum(axis=1).squeeze()        # (H',W')
    cam = np.maximum(cam, 0)
    cam = cv2.resize(cam.astype(np.float32), (512, 512))
    if cam.max() > 0:
        cam /= cam.max()
    return cam, prob


def cam_to_yolo(cam: np.ndarray, threshold: float = 0.4,
                class_id: int = 0) -> str | None:
    binary = (cam > threshold).astype(np.uint8)
    ctrs, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not ctrs:
        return None
    x, y, w, h = cv2.boundingRect(np.vstack(ctrs))
    H, W = cam.shape
    return f"{class_id} {(x+w/2)/W:.6f} {(y+h/2)/H:.6f} {w/W:.6f} {h/H:.6f}"


def save_preview(png_path: Path, cam: np.ndarray, bbox_yolo: str | None,
                 out_path: Path) -> None:
    img = cv2.imread(str(png_path))
    if img is None:
        return
    # 红色热力图叠加
    heat = (cam * 255).astype(np.uint8)
    heat_color = cv2.applyColorMap(heat, cv2.COLORMAP_JET)
    overlay    = cv2.addWeighted(img, 0.55, heat_color, 0.45, 0)
    # 画候选框
    if bbox_yolo:
        parts = bbox_yolo.split()
        cx, cy, bw, bh = [float(p) for p in parts[1:]]
        H, W = img.shape[:2]
        x1 = int((cx - bw/2) * W); y1 = int((cy - bh/2) * H)
        x2 = int((cx + bw/2) * W); y2 = int((cy + bh/2) * H)
        cv2.rectangle(overlay, (x1, y1), (x2, y2), (0, 255, 0), 2)
        cv2.putText(overlay, "hemorrhage", (x1, max(y1-6, 0)),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 1)
    cv2.imwrite(str(out_path), overlay)


def main():
    parser = argparse.ArgumentParser(description="GradCAM 伪标签生成")
    parser.add_argument("--data",     required=True,
                        help="cq500_processed 目录（含 manifest.csv 和 images/）")
    parser.add_argument("--model",    required=True,
                        help="best_classifier.pt 路径")
    parser.add_argument("--preview",  action="store_true",
                        help="保存叠加热力图预览（方便人工核查）")
    parser.add_argument("--threshold",type=float, default=0.4,
                        help="热力图二值化阈值，越高框越小")
    parser.add_argument("--min_conf", type=float, default=0.5,
                        help="切片出血概率低于此值则不生成标签")
    args = parser.parse_args()

    import torch
    import timm
    from tqdm import tqdm

    data_dir    = Path(args.data)
    model_path  = Path(args.model)
    labels_dir  = data_dir / "gradcam_labels"
    labels_dir.mkdir(exist_ok=True)
    if args.preview:
        preview_dir = data_dir / "gradcam_preview"
        preview_dir.mkdir(exist_ok=True)

    if not model_path.exists():
        print(f"模型文件不存在: {model_path}")
        sys.exit(1)

    # 加载模型
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model  = timm.create_model("efficientnet_b3", pretrained=False, num_classes=2)
    model.load_state_dict(torch.load(str(model_path), map_location=device))
    model.to(device).eval()
    print(f"模型加载完成  device={device}")

    # 读 manifest，只处理出血切片
    manifest = data_dir / "manifest.csv"
    with open(manifest, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    pos_rows = [r for r in rows if r.get("label") == "hemorrhage"]
    print(f"出血切片总数: {len(pos_rows)} / {len(rows)}")

    generated, skipped = 0, 0
    for r in tqdm(pos_rows, desc="GradCAM"):
        png_path = data_dir / "images" / r["slice_name"]
        if not png_path.exists():
            skipped += 1
            continue

        inp = build_input_tensor(png_path)
        if inp is None:
            skipped += 1
            continue

        cam, prob = compute_gradcam(model, inp, device)
        if prob < args.min_conf:
            # 置信度不足，跳过（可能是假阳性体积级标签的切片）
            skipped += 1
            continue

        bbox = cam_to_yolo(cam, threshold=args.threshold)
        lbl_path = labels_dir / r["slice_name"].replace(".png", ".txt")
        lbl_path.write_text(bbox or "")  # 空文件 = 负样本（无病灶区域）

        if args.preview and bbox:
            save_preview(png_path, cam, bbox,
                         preview_dir / r["slice_name"])

        generated += 1

    print(f"\n完成：{generated} 张生成标签，{skipped} 张跳过")
    print(f"标签目录: {labels_dir}")
    if args.preview:
        print(f"预览目录: {preview_dir}  ← 请人工抽检修正明显错误的框")
    print("\n下一步：")
    print("  1. 抽检 gradcam_preview/ 里的图，修正明显错误的 bbox")
    print("  2. 将 gradcam_labels/ 重命名为 yolo_labels/")
    print("  3. 将 manifest.csv 里 has_lesion=True 的行对应字段补上")
    print("  4. 运行 train_detector.py")


if __name__ == "__main__":
    main()
