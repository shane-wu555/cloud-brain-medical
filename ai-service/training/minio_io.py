"""MinIO 读写工具，供训练脚本共用"""

import io
import os
import tempfile
from pathlib import Path

from minio import Minio
from minio.error import S3Error
from tqdm import tqdm


def client() -> Minio:
    return Minio(
        os.getenv("MINIO_ENDPOINT", "localhost:9000"),
        access_key=os.getenv("MINIO_ACCESS_KEY", "minioadmin"),
        secret_key=os.getenv("MINIO_SECRET_KEY", "minioadmin"),
        secure=os.getenv("MINIO_SECURE", "false").lower() == "true",
    )


def ensure_bucket(mc: Minio, bucket: str) -> None:
    if not mc.bucket_exists(bucket):
        mc.make_bucket(bucket)


def download_dir(mc: Minio, bucket: str, prefix: str, local_dir: Path) -> None:
    """将 MinIO prefix 下所有文件下载到 local_dir"""
    local_dir.mkdir(parents=True, exist_ok=True)
    objects = list(mc.list_objects(bucket, prefix=prefix, recursive=True))
    for obj in tqdm(objects, desc=f"下载 {bucket}/{prefix}"):
        rel = obj.object_name[len(prefix):].lstrip("/")
        dst = local_dir / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        mc.fget_object(bucket, obj.object_name, str(dst))


def upload_file(mc: Minio, bucket: str, object_name: str, local_path: Path) -> None:
    ensure_bucket(mc, bucket)
    mc.fput_object(bucket, object_name, str(local_path))
    print(f"已上传: {bucket}/{object_name}")


def upload_dir(mc: Minio, bucket: str, prefix: str, local_dir: Path,
               glob: str = "**/*") -> None:
    ensure_bucket(mc, bucket)
    files = [f for f in local_dir.glob(glob) if f.is_file()]
    for f in tqdm(files, desc=f"上传 {bucket}/{prefix}"):
        rel = f.relative_to(local_dir).as_posix()
        mc.fput_object(bucket, f"{prefix}/{rel}", str(f))


def object_exists(mc: Minio, bucket: str, object_name: str) -> bool:
    try:
        mc.stat_object(bucket, object_name)
        return True
    except S3Error:
        return False
