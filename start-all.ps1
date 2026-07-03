param(
    [switch]$SkipDocker,
    [switch]$SkipBackendBuild,
    [switch]$SkipBackend,
    [switch]$SkipAi,
    [switch]$SkipDicom,
    [switch]$SkipFrontend,
    [switch]$SkipMiniapp,
    [switch]$InstallFrontendDeps,
    [switch]$InstallMiniappDeps,
    [string]$DbHost = "localhost",
    [string]$DbName = "cloud_brain_medical",
    [string]$DbUser = "cloudbrain",
    [string]$DbPassword = "cloudbrain"
)

$ErrorActionPreference = "Stop"

$root = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$logsDir = Join-Path $root "logs"
$launchersDir = Join-Path $logsDir "launchers"
New-Item -ItemType Directory -Force -Path $logsDir, $launchersDir | Out-Null

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Start-LoggedProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    $stdout = Join-Path $logsDir "$Name.out.log"
    $stderr = Join-Path $logsDir "$Name.err.log"
    Start-Process -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden
    Write-Host "Started $Name. Logs: logs\$Name.out.log, logs\$Name.err.log" -ForegroundColor Green
}

function New-Launcher {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string[]]$Lines
    )

    $path = Join-Path $launchersDir "$Name.ps1"
    $Lines | Set-Content -Path $path -Encoding UTF8
    return $path
}

function Get-Python {
    param([string]$VenvDir)

    $venvPython = Join-Path $VenvDir "Scripts\python.exe"
    if (Test-Path $venvPython) {
        return $venvPython
    }
    return "python"
}

if (-not $SkipDocker) {
    Write-Step "Starting Docker infrastructure"
    docker compose -f (Join-Path $root "docker\docker-compose.yml") up -d
}

if (-not $SkipBackend) {
    if (-not $SkipBackendBuild) {
        Write-Step "Packaging backend services"
        Push-Location (Join-Path $root "backend")
        try {
            mvn -DskipTests package
        }
        finally {
            Pop-Location
        }
    }

    Write-Step "Starting backend services"
    $backendServices = @(
        @{ name = "gateway-service";        jar = "backend\gateway-service\target\gateway-service-0.1.0-SNAPSHOT.jar";        schema = $null },
        @{ name = "auth-service";           jar = "backend\auth-service\target\auth-service-0.1.0-SNAPSHOT.jar";           schema = "auth";           env = "AUTH_DB_URL" },
        @{ name = "patient-service";        jar = "backend\patient-service\target\patient-service-0.1.0-SNAPSHOT.jar";        schema = "patient";        env = "PATIENT_DB_URL" },
        @{ name = "doctor-service";         jar = "backend\doctor-service\target\doctor-service-0.1.0-SNAPSHOT.jar";         schema = "doctor";         env = "DOCTOR_DB_URL" },
        @{ name = "appointment-service";    jar = "backend\appointment-service\target\appointment-service-0.1.0-SNAPSHOT.jar";    schema = "appointment";    env = "APPOINTMENT_DB_URL" },
        @{ name = "medical-record-service"; jar = "backend\medical-record-service\target\medical-record-service-0.1.0-SNAPSHOT.jar"; schema = "medical_record"; env = "MEDICAL_RECORD_DB_URL" },
        @{ name = "medical-order-service";  jar = "backend\medical-order-service\target\medical-order-service-0.1.0-SNAPSHOT.jar";  schema = "medical_order";  env = "MEDICAL_ORDER_DB_URL" },
        @{ name = "pharmacy-service";       jar = "backend\pharmacy-service\target\pharmacy-service-0.1.0-SNAPSHOT.jar";       schema = "pharmacy";       env = "PHARMACY_DB_URL" },
        @{ name = "cashier-service";        jar = "backend\cashier-service\target\cashier-service-0.1.0-SNAPSHOT.jar";        schema = "cashier";        env = "CASHIER_DB_URL" },
        @{ name = "report-service";         jar = "backend\report-service\target\report-service-0.1.0-SNAPSHOT.jar";         schema = "report";         env = "REPORT_DB_URL" },
        @{ name = "audit-service";          jar = "backend\audit-service\target\audit-service-0.1.0-SNAPSHOT.jar";          schema = "audit";          env = "AUDIT_DB_URL" }
    )

    foreach ($service in $backendServices) {
        $jarPath = Join-Path $root $service.jar
        if (-not (Test-Path $jarPath)) {
            Write-Host "Skipped $($service.name): JAR not found at $($service.jar)" -ForegroundColor Yellow
            continue
        }

        $lines = @(
            "`$env:DB_USERNAME = '$DbUser'",
            "`$env:DB_PASSWORD = '$DbPassword'",
            "`$env:MINIO_ENDPOINT = 'http://127.0.0.1:9000'",
            "`$env:MINIO_ACCESS_KEY = 'minioadmin'",
            "`$env:MINIO_SECRET_KEY = 'minioadmin'",
            "`$env:MINIO_MEDICAL_BUCKET = 'medical-imaging'"
        )
        if ($service.schema) {
            $dbUrl = "jdbc:postgresql://${DbHost}:5432/${DbName}?currentSchema=$($service.schema)"
            $lines += "`$env:$($service.env) = '$dbUrl'"
        }
        $lines += "java -jar '$jarPath'"

        $launcher = New-Launcher -Name $service.name -Lines $lines
        Start-LoggedProcess -Name $service.name `
            -FilePath "powershell" `
            -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $launcher) `
            -WorkingDirectory $root
        Start-Sleep -Seconds 1
    }
}

if (-not $SkipAi) {
    Write-Step "Starting AI service"
    $aiPython = Get-Python -VenvDir (Join-Path $root "ai-service\.venv")
    $aiLauncher = New-Launcher -Name "ai-service" -Lines @(
        "`$env:PYTHONPATH = '$root\ai-service'",
        "if (Test-Path '$root\ai-service\.env') { Get-Content '$root\ai-service\.env' | Where-Object { `$_ -and -not `$_.StartsWith('#') -and `$_.Contains('=') } | ForEach-Object { `$k, `$v = `$_.Split('=', 2); [Environment]::SetEnvironmentVariable(`$k.Trim(), `$v.Trim(), 'Process') } }",
        "if (-not `$env:AI_PROVIDER) { `$env:AI_PROVIDER = 'mock' }",
        "if (-not `$env:AI_ALLOW_FALLBACK) { `$env:AI_ALLOW_FALLBACK = 'true' }",
        "if (-not `$env:AI_TIMEOUT_SECONDS) { `$env:AI_TIMEOUT_SECONDS = '60' }",
        "& '$aiPython' -m uvicorn app.main:app --app-dir '$root\ai-service' --host 0.0.0.0 --port 8000"
    )
    Start-LoggedProcess -Name "ai-service" `
        -FilePath "powershell" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $aiLauncher) `
        -WorkingDirectory $root
}

if (-not $SkipDicom) {
    Write-Step "Starting DICOM service"
    $dicomPython = Get-Python -VenvDir (Join-Path $root "dicom-service\.venv")
    Start-LoggedProcess -Name "dicom-service" `
        -FilePath $dicomPython `
        -ArgumentList @("-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8765", "--reload") `
        -WorkingDirectory (Join-Path $root "dicom-service")
}

if (-not $SkipFrontend) {
    Write-Step "Starting PC frontend"
    if ($InstallFrontendDeps) {
        Push-Location (Join-Path $root "frontend")
        try {
            npm install
        }
        finally {
            Pop-Location
        }
    }
    Start-LoggedProcess -Name "frontend" `
        -FilePath "cmd.exe" `
        -ArgumentList @("/c", "npm run dev -- --host 0.0.0.0") `
        -WorkingDirectory (Join-Path $root "frontend")
}

if (-not $SkipMiniapp) {
    Write-Step "Starting patient miniapp"
    if ($InstallMiniappDeps) {
        Push-Location (Join-Path $root "patient-miniapp")
        try {
            npm install
        }
        finally {
            Pop-Location
        }
    }
    Start-LoggedProcess -Name "patient-miniapp" `
        -FilePath "cmd.exe" `
        -ArgumentList @("/c", "npm run dev:mp-weixin") `
        -WorkingDirectory (Join-Path $root "patient-miniapp")
}

Write-Host "`nAll requested services have been launched." -ForegroundColor Cyan
Write-Host "Gateway:  http://localhost:8080"
Write-Host "Frontend: http://localhost:5173"
Write-Host "AI:       http://localhost:8000/health"
Write-Host "DICOM:    http://localhost:8765/docs"
Write-Host "Logs:     $logsDir"
