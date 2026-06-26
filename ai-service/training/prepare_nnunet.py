"""
阶段④：准备 nnU-Net v2 数据集（分割任务）
将 CT500 数据组织为 nnU-Net Task 格式，然后运行预处理和训练命令

用法:
    # 1. 准备数据集
    python training/prepare_nnunet.py \
        --input  /data/CT500 \
        --output /data/nnunet_raw \
        --masks  /data/CT500/masks   # 分割掩码目录（.nii.gz，0=背景,1=出血,2=缺血）

    # 2. 运行 nnU-Net 预处理（在 conda 环境中执行）
    nnUNetv2_plan_and_preprocess -d 100 --verify_dataset_integrity

    # 3. 训练（2D 配置适合头颅 CT 逐层分析）
    nnUNetv2_train 100 2d 0   # fold 0
    nnUNetv2_train 100 2d 1   # fold 1
    ...

    # 4. 推理
    nnUNetv2_predict -i /data/test_images -o /data/test_predictions \
        -d 100 -c 2d -f all --save_probabilities
"""

import argparse
import json
import shutil
from pathlib import Path

import SimpleITK as sitk
import numpy as np
from tqdm import tqdm


DATASET_ID   = 100          # 自定义 Task ID
DATASET_NAME = "CT500HeadCT"
LABELS = {
    "background":  0,
    "hemorrhage":  1,
    "ischemia":    2,
}


def load_volume(patient_dir: Path) -> sitk.Image:
    """支持 DICOM 目录 或 NIfTI 文件"""
    nii = list(patient_dir.glob("*.nii.gz")) + list(patient_dir.glob("*.nii"))
    if nii:
        return sitk.ReadImage(str(nii[0]))

    reader = sitk.ImageSeriesReader()
    files  = reader.GetGDCMSeriesFileNames(str(patient_dir))
    if not files:
        raise FileNotFoundError(f"无法读取: {patient_dir}")
    reader.SetFileNames(files)
    return reader.Execute()


def ensure_hu(img: sitk.Image) -> sitk.Image:
    """确保图像是 float32 HU 值"""
    arr = sitk.GetArrayFromImage(img).astype(np.float32)
    result = sitk.GetImageFromArray(arr)
    result.CopyInformation(img)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="准备 nnU-Net 数据集")
    parser.add_argument("--input",   required=True, help="CT500 根目录")
    parser.add_argument("--output",  required=True, help="nnU-Net raw data 根目录")
    parser.add_argument("--masks",   required=True, help="分割掩码目录")
    parser.add_argument("--dataset_id", type=int, default=DATASET_ID)
    args = parser.parse_args()

    dataset_dir = Path(args.output) / f"Dataset{args.dataset_id:03d}_{DATASET_NAME}"
    images_dir  = dataset_dir / "imagesTr"
    labels_dir  = dataset_dir / "labelsTr"
    images_dir.mkdir(parents=True, exist_ok=True)
    labels_dir.mkdir(parents=True, exist_ok=True)

    input_dir = Path(args.input)
    masks_dir = Path(args.masks)

    patient_dirs = sorted(p for p in input_dir.iterdir() if p.is_dir())
    training_cases = []

    for i, patient_dir in enumerate(tqdm(patient_dirs, desc="准备患者数据")):
        patient_id = patient_dir.name
        case_id    = f"CT500_{i:04d}"

        # 查找掩码（支持 patient_id.nii.gz 或 patient_id_mask.nii.gz）
        mask_candidates = [
            masks_dir / f"{patient_id}.nii.gz",
            masks_dir / f"{patient_id}_mask.nii.gz",
            masks_dir / f"{patient_id}_label.nii.gz",
        ]
        mask_path = next((m for m in mask_candidates if m.exists()), None)
        if mask_path is None:
            print(f"[跳过] {patient_id}：找不到对应掩码")
            continue

        try:
            img  = load_volume(patient_dir)
            mask = sitk.ReadImage(str(mask_path))
        except Exception as e:
            print(f"[跳过] {patient_id}: {e}")
            continue

        # nnU-Net 期望图像名格式: {case_id}_0000.nii.gz（通道0）
        sitk.WriteImage(ensure_hu(img),  str(images_dir / f"{case_id}_0000.nii.gz"))
        # 掩码必须是 int 类型
        mask_arr  = sitk.GetArrayFromImage(mask).astype(np.uint8)
        mask_out  = sitk.GetImageFromArray(mask_arr)
        mask_out.CopyInformation(mask)
        sitk.WriteImage(mask_out, str(labels_dir / f"{case_id}.nii.gz"))

        training_cases.append({"image": f"./imagesTr/{case_id}_0000.nii.gz",
                                "label": f"./labelsTr/{case_id}.nii.gz"})

    # dataset.json（nnU-Net v2 格式）
    dataset_json = {
        "channel_names": {"0": "CT"},
        "labels": {name: idx for name, idx in LABELS.items()},
        "numTraining": len(training_cases),
        "file_ending": ".nii.gz",
        "dataset_name": DATASET_NAME,
        "reference": "CT500 Head CT Dataset",
        "licence": "see dataset source",
        "release": "1.0",
        "training": training_cases,
    }
    (dataset_dir / "dataset.json").write_text(
        json.dumps(dataset_json, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    print(f"\n数据集准备完成: {dataset_dir}")
    print(f"共 {len(training_cases)} 个训练样本\n")
    print("=" * 60)
    print("接下来请在安装了 nnU-Net 的环境中执行：")
    print(f"  export nnUNet_raw={Path(args.output).resolve()}")
    print( "  export nnUNet_preprocessed=/data/nnunet_preprocessed")
    print( "  export nnUNet_results=/models/nnunet_results")
    print(f"  nnUNetv2_plan_and_preprocess -d {args.dataset_id} --verify_dataset_integrity")
    print(f"  nnUNetv2_train {args.dataset_id} 2d 0   # 训练 fold 0")
    print(f"  nnUNetv2_train {args.dataset_id} 2d all # 训练全部 fold")
    print("=" * 60)


if __name__ == "__main__":
    main()
