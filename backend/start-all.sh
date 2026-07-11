#!/bin/bash

BASE_DIR=$(pwd)

LOG_DIR=$BASE_DIR/logs

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


echo "========== Build services =========="

mvn clean package -DskipTests


echo "========== Start services =========="


for SERVICE in "${SERVICES[@]}"
do

    echo "Starting $SERVICE ..."

    JAR=$(ls $SERVICE/target/*.jar | head -n 1)

    if [ -f "$JAR" ]; then

        nohup java -jar $JAR \
        > $LOG_DIR/$SERVICE.log 2>&1 &

        echo "$SERVICE started"

    else

        echo "$SERVICE jar not found"

    fi

done


echo "========== All services started =========="
