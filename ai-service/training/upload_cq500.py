"""
将 CQ500 数据集上传到 MinIO，同时将 reads.csv 转为统一 labels.json 格式。

用法:
    python training/upload_cq500.py --input F:/ShiXunClass/data/CQ500

上传后 MinIO 结构:
    training-data/
    └── CQ500/
        ├── CQ500-CT-0.zip        ← 病人 CT 数据
        ├── CQ500-CT-1.zip
        ├── ...
        └── labels.json           ← 转换好的标注文件
"""

import argparse
import csv
import json
import os
import time
from pathlib import Path

from minio import Minio
from minio.error import S3Error
from tqdm import tqdm


BUCKET = "training-data"
PREFIX = "CQ500"


def convert_reads_csv(csv_path: Path) -> list[dict]:
    """
    reads.csv → [{patientId, label, subtypes, bboxes:[]}]

    标签规则（3位放射科医生多数投票）:
      R1:ICH + R2:ICH + R3:ICH >= 2 → hemorrhage
      否则 → normal

    CQ500 没有缺血标签，也没有 bounding box，
    bboxes 留空（检测训练需要单独标注）。
    """
    with open(csv_path, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    labels = []
    for row in rows:
        ich_votes = sum(int(row.get(f"R{r}:ICH", 0)) for r in [1, 2, 3])
        label = "hemorrhage" if ich_votes >= 2 else "normal"

        # 细化出血亚型（供后续分析用，不参与三分类训练）
        subtypes = {}
        for r in [1, 2, 3]:
            for t in ["IPH", "IVH", "SDH", "EDH", "SAH", "Fracture",
                      "MassEffect", "MidlineShift"]:
                key = f"R{r}:{t}"
                if key in row:
                    subtypes.setdefault(t, []).append(int(row[key]))

        labels.append({
            "patientId": row["name"],
            "label":     label,
            "bboxes":    [],   # CQ500 无像素级标注
            "subtypes":  {k: (sum(v) >= 2) for k, v in subtypes.items()},
            "category":  row.get("Category", ""),
        })

    # 统计
    counts = {}
    for item in labels:
        counts[item["label"]] = counts.get(item["label"], 0) + 1
    print(f"标注转换完成：{counts}，共 {len(labels)} 个病人")
    return labels


def upload_file(mc: Minio, local_path: Path, object_name: str,
                content_type: str = "application/octet-stream") -> None:
    mc.fput_object(BUCKET, object_name, str(local_path),
                   content_type=content_type)


def already_uploaded(mc: Minio, object_name: str) -> bool:
    try:
        mc.stat_object(BUCKET, object_name)
        return True
    except S3Error:
        return False


def main() -> None:
    parser = argparse.ArgumentParser(description="上传 CQ500 到 MinIO")
    parser.add_argument("--input",    default="F:/ShiXunClass/data/CQ500",
                        help="CQ500 根目录")
    parser.add_argument("--endpoint", default="localhost:9000")
    parser.add_argument("--ak",       default="minioadmin")
    parser.add_argument("--sk",       default="minioadmin")
    parser.add_argument("--resume",   action="store_true",
                        help="跳过已上传的文件（断点续传）")
    args = parser.parse_args()

    data_dir = Path(args.input)
    orig_dir = data_dir / "CQ500_orig"

    if not orig_dir.exists():
        print(f"找不到目录: {orig_dir}")
        return

    # 连接 MinIO
    mc = Minio(args.endpoint, access_key=args.ak,
               secret_key=args.sk, secure=False)

    # 创建 bucket
    if not mc.bucket_exists(BUCKET):
        mc.make_bucket(BUCKET)
        print(f"已创建 bucket: {BUCKET}")
    else:
        print(f"bucket 已存在: {BUCKET}")

    # 1. 转换并上传 labels.json
    csv_path = orig_dir / "reads.csv"
    labels = convert_reads_csv(csv_path)
    labels_json = json.dumps(labels, ensure_ascii=False, indent=2).encode("utf-8")
    import io
    mc.put_object(BUCKET, f"{PREFIX}/labels.json",
                  io.BytesIO(labels_json), len(labels_json),
                  content_type="application/json")
    print(f"已上传 labels.json → {BUCKET}/{PREFIX}/labels.json")

    # 2. 上传 zip 文件
    zip_files = sorted(orig_dir.glob("CQ500-CT-*.zip"))
    print(f"\n开始上传 {len(zip_files)} 个 CT zip 文件（共约 {sum(f.stat().st_size for f in zip_files)/1024**3:.1f} GB）")
    print("提示：上传到本机 MinIO 大约需要 3~10 分钟\n")

    skipped = 0
    uploaded = 0
    errors = []
    t0 = time.time()

    for zip_file in tqdm(zip_files, desc="上传进度", unit="个"):
        object_name = f"{PREFIX}/{zip_file.name}"

        if args.resume and already_uploaded(mc, object_name):
            skipped += 1
            continue

        try:
            upload_file(mc, zip_file, object_name)
            uploaded += 1
        except Exception as e:
            errors.append((zip_file.name, str(e)))
            tqdm.write(f"[错误] {zip_file.name}: {e}")

    elapsed = time.time() - t0
    print(f"\n上传完成！")
    print(f"  新上传: {uploaded} 个")
    print(f"  已跳过: {skipped} 个（--resume）")
    print(f"  失败:   {len(errors)} 个")
    print(f"  耗时:   {elapsed/60:.1f} 分钟")
    if errors:
        print("\n失败文件:")
        for name, err in errors:
            print(f"  {name}: {err}")

    print(f"\nMinIO 路径: {BUCKET}/{PREFIX}/")
    print("下一步（预处理）:")
    print(f"  python training/preprocess_cq500.py \\")
    print(f"      --input  minio://{BUCKET}/{PREFIX} \\")
    print(f"      --output minio://training-output/cq500_processed")


if __name__ == "__main__":
    main()
