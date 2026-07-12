#!/bin/bash

set -e

PROJECT_DIR=/opt/cloud-brain-medical

BACKEND_DIR=$PROJECT_DIR/backend
FRONTEND_DIR=$PROJECT_DIR/frontend
DOCKER_DIR=$PROJECT_DIR/docker

LOG_DIR=$BACKEND_DIR/logs

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

echo "公网 IP：${PUBLIC_IP}"
echo "扫码基础地址：${PAYMENT_PUBLIC_SCAN_BASE_URL}"

echo "================================="
echo " Cloud Brain Medical Deployment "
echo "================================="


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
