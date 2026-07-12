#!/bin/bash

cd ~/autodl-tmp/cloud-brain-medical/ai-service

pkill -f "uvicorn app.main:app"

nohup uvicorn app.main:app \
--host 0.0.0.0 \
--port 8000 \
--workers 1 \
> ai.log 2>&1 &

echo "AI service started"
