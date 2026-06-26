"""
把 MinIO 里的训练数据下载到本地，打包成 AutoDL 可上传的 tar 文件。

用法:
    python training/export_for_autodl.py --output F:/tmp/autodl_upload
"""

import argparse
import tarfile
from pathlib import Path
from tqdm import tqdm
from minio import Minio
import os


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="F:/tmp/autodl_upload")
    parser.add_argument("--endpoint", default="localhost:9000")
    parser.add_argument("--ak", default="minioadmin")
    parser.add_argument("--sk", default="minioadmin")
    args = parser.parse_args()

    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)
    data_dir = out_dir / "cq500_processed"
    (data_dir / "images").mkdir(parents=True, exist_ok=True)

    mc = Minio(args.endpoint, access_key=args.ak, secret_key=args.sk, secure=False)

    # 1. 下载 manifest.csv
    mc.fget_object("training-output", "cq500_processed/manifest.csv",
                   str(data_dir / "manifest.csv"))
    print("manifest.csv 下载完成")

    # 2. 下载所有 PNG 切片
    objects = list(mc.list_objects(
        "training-output", prefix="cq500_processed/images/", recursive=True))
    print(f"开始下载 {len(objects)} 张切片...")

    for obj in tqdm(objects, desc="下载", unit="张"):
        fname = Path(obj.object_name).name
        dst = data_dir / "images" / fname
        if not dst.exists():
            mc.fget_object("training-output", obj.object_name, str(dst))

    print(f"\n下载完成，数据目录: {data_dir}")

    # 3. 打包训练脚本
    scripts_dir = Path(__file__).parent
    for script in ["train_classifier.py", "train_detector.py", "minio_io.py"]:
        src = scripts_dir / script
        if src.exists():
            import shutil
            shutil.copy2(src, data_dir / script)

    # 4. 打包成 tar（AutoDL 数据集上传支持 tar）
    tar_path = out_dir / "cq500_train.tar"
    print(f"\n打包中 → {tar_path}")
    with tarfile.open(tar_path, "w") as tar:
        tar.add(data_dir, arcname="cq500_processed")
    size_gb = tar_path.stat().st_size / 1024**3
    print(f"打包完成：{tar_path}（{size_gb:.1f} GB）")
    print("\n接下来：")
    print("1. 登录 AutoDL → 数据集 → 创建数据集 → 上传 cq500_train.tar")
    print("2. 租 RTX 3080 Ti 或 vGPU-32GB 实例")
    print("3. 实例内挂载数据集到 /root/autodl-tmp/")
    print("4. 运行训练命令")


if __name__ == "__main__":
    main()
