#!/bin/bash

set -e

PROJECT_DIR=/opt/cloud-brain-medical

BACKEND_DIR=$PROJECT_DIR/backend
FRONTEND_DIR=$PROJECT_DIR/frontend
DOCKER_DIR=$PROJECT_DIR/docker
FRP_DIR="${FRP_DIR:-/opt/frp_0.61.1_linux_amd64}"
FRPS_CONFIG="${FRPS_CONFIG:-$FRP_DIR/frps.toml}"

LOG_DIR=$BACKEND_DIR/logs
mkdir -p $LOG_DIR

PUBLIC_IP=$(curl -fsS \
    --connect-timeout 3 \
    --max-time 5 \
    http://100.100.100.200/latest/meta-data/eipv4 \
    2>/dev/null || true)

if [[ ! "$PUBLIC_IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo "ERROR: 无法从阿里云实例元数据获取公网 IP：[$PUBLIC_IP]"
    exit 1
fi

export PAYMENT_PUBLIC_SCAN_BASE_URL="http://${PUBLIC_IP}"
export AI_SERVICE_URL="${AI_SERVICE_URL:-http://127.0.0.1:18000}"

echo "公网 IP：${PUBLIC_IP}"
echo "扫码基础地址：${PAYMENT_PUBLIC_SCAN_BASE_URL}"

echo "================================="
echo " Cloud Brain Medical Deployment "
echo "================================="
echo "AI Service URL: ${AI_SERVICE_URL}"
echo "FRP server config: ${FRPS_CONFIG}"

#################################
# 0. Start FRP server
#################################

echo ""
echo "========== Start FRP Server =========="

if [ -x "$FRP_DIR/frps" ] && [ -f "$FRPS_CONFIG" ]; then

    pkill -f "frps -c $FRPS_CONFIG" || true

    nohup "$FRP_DIR/frps" -c "$FRPS_CONFIG" \
    > "$LOG_DIR/frps.log" 2>&1 &

    echo "frps started"

else

    echo "WARN: frps not found or config missing, skipped"
    echo "      expected binary: $FRP_DIR/frps"
    echo "      expected config: $FRPS_CONFIG"

fi

# ==============================
# RDS PostgreSQL Configuration
# ==============================

export DB_HOST="pgm-2ze9qv77u8k2847vmo.pg.rds.aliyuncs.com"

export DB_USERNAME="postgreSQL_user"

export DB_PASSWORD="postgreDB1"


export AUTH_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=auth"

export PATIENT_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=patient"

export DOCTOR_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=doctor"

export APPOINTMENT_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=appointment"

export MEDICAL_ORDER_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=medical_order"

export MEDICAL_RECORD_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=medical_record"

export CASHIER_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=cashier"

export PHARMACY_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=pharmacy"

export REPORT_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=report"

export AUDIT_DB_URL="jdbc:postgresql://${DB_HOST}:5432/cloud_brain_medical?currentSchema=audit"

#################################
# 1. 启动 Docker 基础服务
#################################

echo ""
echo "========== Start Docker Services =========="


cd $DOCKER_DIR

docker compose up -d


echo "Waiting docker services..."

sleep 10


docker ps



#################################
# 2. 构建后端
#################################

echo ""
echo "========== Build Backend =========="


cd $BACKEND_DIR


mvn clean package -DskipTests



#################################
# 3. 停止旧 Java 服务
#################################

echo ""
echo "========== Stop Old Services =========="


pkill -f "java -jar" || true


sleep 3



#################################
# 4. 启动 Spring Boot 服务
#################################


echo ""
echo "========== Start Backend Services =========="


mkdir -p $LOG_DIR


SERVICES=(

auth-service
patient-service
doctor-service
appointment-service
medical-record-service
medical-order-service
pharmacy-service
report-service
audit-service
cashier-service
gateway-service

)



for SERVICE in "${SERVICES[@]}"
do

    echo "Starting $SERVICE ..."


    JAR=$(ls $SERVICE/target/*.jar | head -n 1)


    if [ -f "$JAR" ]; then

        nohup java -jar $JAR \
        > $LOG_DIR/$SERVICE.log 2>&1 &


        echo "$SERVICE started"

    else

        echo "ERROR: $SERVICE jar not found"

    fi

done



#################################
# 5. 构建前端
#################################


echo ""
echo "========== Build Frontend =========="


cd $FRONTEND_DIR


npm install

npm run build



#################################
# 6. 重启 Nginx
#################################


echo ""
echo "========== Restart Nginx =========="


nginx -t

systemctl restart nginx



#################################
# 7. 检查状态
#################################


echo ""
echo "========== Deployment Finished =========="


echo ""
echo "Listening ports:"


ss -tlnp | grep -E "80|8080|5432|6379|9000|9200"



echo ""
echo "Website:"
echo "http://$(curl -s ifconfig.me)"
