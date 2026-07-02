$DB_HOST = "pgm-2ze9qv77u8k2847vmo.pg.rds.aliyuncs.com"
$DB_NAME = "cloud_brain_medical"
$DB_USER = "postgreSQL_user"
$DB_PASS = "postgreDB1"
$SSL    = "sslmode=disable"
$MINIO_ENDPOINT = "http://127.0.0.1:9000"
$MINIO_ACCESS_KEY = "minioadmin"
$MINIO_SECRET_KEY = "minioadmin"
$MINIO_BUCKET = "medical-imaging"

$services = @(
    @{ name = "gateway-service";        jar = "backend\gateway-service\target\gateway-service-0.1.0-SNAPSHOT.jar";        dbUrl = "" },
    @{ name = "auth-service";           jar = "backend\auth-service\target\auth-service-0.1.0-SNAPSHOT.jar";           dbUrl = "AUTH_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=auth&${SSL}" },
    @{ name = "patient-service";        jar = "backend\patient-service\target\patient-service-0.1.0-SNAPSHOT.jar";        dbUrl = "PATIENT_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=patient&${SSL}" },
    @{ name = "doctor-service";         jar = "backend\doctor-service\target\doctor-service-0.1.0-SNAPSHOT.jar";         dbUrl = "DOCTOR_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=doctor&${SSL}" },
    @{ name = "appointment-service";    jar = "backend\appointment-service\target\appointment-service-0.1.0-SNAPSHOT.jar";    dbUrl = "APPOINTMENT_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=appointment&${SSL}" },
    @{ name = "medical-record-service"; jar = "backend\medical-record-service\target\medical-record-service-0.1.0-SNAPSHOT.jar"; dbUrl = "MEDICAL_RECORD_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=medical_record&${SSL}" },
    @{ name = "medical-order-service";  jar = "backend\medical-order-service\target\medical-order-service-0.1.0-SNAPSHOT.jar";  dbUrl = "MEDICAL_ORDER_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=medical_order&${SSL}" },
    @{ name = "pharmacy-service";       jar = "backend\pharmacy-service\target\pharmacy-service-0.1.0-SNAPSHOT.jar";       dbUrl = "PHARMACY_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=pharmacy&${SSL}" },
    @{ name = "cashier-service";        jar = "backend\cashier-service\target\cashier-service-0.1.0-SNAPSHOT.jar";        dbUrl = "CASHIER_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=cashier&${SSL}" },
    @{ name = "report-service";         jar = "backend\report-service\target\report-service-0.1.0-SNAPSHOT.jar";         dbUrl = "REPORT_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=report&${SSL}" },
    @{ name = "audit-service";          jar = "backend\audit-service\target\audit-service-0.1.0-SNAPSHOT.jar";          dbUrl = "AUDIT_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?currentSchema=audit&${SSL}" }
)

$root = if ($PSScriptRoot) { $PSScriptRoot } else { $PWD.Path }

foreach ($svc in $services) {
    $jarPath = Join-Path $root $svc.jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "[$($svc.name)] JAR not found, skipping" -ForegroundColor Yellow
        continue
    }

    # Write a temp launcher script for this service
    $tempScript = Join-Path $env:TEMP "$($svc.name)-launch.ps1"

    $lines = @(
        "`$env:DB_USERNAME = '$DB_USER'",
        "`$env:DB_PASSWORD = '$DB_PASS'",
        "`$env:MINIO_ENDPOINT = '$MINIO_ENDPOINT'",
        "`$env:MINIO_ACCESS_KEY = '$MINIO_ACCESS_KEY'",
        "`$env:MINIO_SECRET_KEY = '$MINIO_SECRET_KEY'",
        "`$env:MINIO_MEDICAL_BUCKET = '$MINIO_BUCKET'"
    )
    if ($svc.dbUrl -ne "") {
        $kv = $svc.dbUrl -split "=", 2
        $lines += "`$env:$($kv[0]) = '$($kv[1])'"
    }
    $lines += "Write-Host 'Starting $($svc.name)...' -ForegroundColor Cyan"
    $lines += "java -jar `"$jarPath`""

    $lines | Out-File -FilePath $tempScript -Encoding UTF8

    Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tempScript
    Write-Host "[$($svc.name)] window opened" -ForegroundColor Green
    Start-Sleep -Seconds 2
}

Write-Host "`nAll services launched. Check taskbar for PowerShell windows." -ForegroundColor Cyan
