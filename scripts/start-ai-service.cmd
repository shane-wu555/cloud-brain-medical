@echo off
setlocal

cd /d "%~dp0.."

if exist "ai-service\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("ai-service\.env") do (
    if not "%%A"=="" set "%%A=%%B"
  )
) else (
  echo ai-service\.env not found. Using environment variables and built-in defaults.
  echo Copy ai-service\.env.example to ai-service\.env and fill AI_OPENAI_API_KEY to use Bailian DeepSeek.
)

if "%AI_PROVIDER%"=="" set "AI_PROVIDER=mock"
if "%AI_OPENAI_BASE_URL%"=="" set "AI_OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1"
if "%AI_OPENAI_MODEL%"=="" set "AI_OPENAI_MODEL=deepseek-v4-pro"
if "%AI_ALLOW_FALLBACK%"=="" set "AI_ALLOW_FALLBACK=true"
if "%AI_TIMEOUT_SECONDS%"=="" set "AI_TIMEOUT_SECONDS=60"

set "PYTHONPATH=%CD%\ai-service"

python -m uvicorn app.main:app --app-dir ai-service --host 0.0.0.0 --port 8000
