#!/bin/bash

echo "===== Starting Cloud Brain AI Services ====="


echo "[1/2] Starting frpc..."

pkill -f "frpc -c frpc.toml" || true

cd /opt/frp_0.61.1_linux_amd64

nohup ./frpc \
-c frpc.toml \
> frpc.log 2>&1 &

echo "frpc started"


echo "[2/2] Starting AI service..."

cd ~/autodl-tmp/cloud-brain-medical/ai-service

pkill -f "uvicorn app.main:app" || true

nohup uvicorn app.main:app \
--host 0.0.0.0 \
--port 8000 \
> ai.log 2>&1 &

echo "AI service started"

echo "===== All services started ====="