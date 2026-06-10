# PostgreSQL 与 Flyway 说明

V1 已将核心内存仓储替换为 PostgreSQL + Flyway。

## 服务 schema

- `auth-service`：`auth`
- `appointment-service`：`appointment`
- `medical-record-service`：`medical_record`

## 默认连接

```text
jdbc:postgresql://localhost:5432/cloud_brain_medical
username: cloudbrain
password: cloudbrain
```

可通过环境变量覆盖：

- `DB_USERNAME`
- `DB_PASSWORD`
- `AUTH_DB_URL`
- `APPOINTMENT_DB_URL`
- `MEDICAL_RECORD_DB_URL`

## 本地启动数据库

```bash
docker compose -f docker/docker-compose.yml up -d postgres
```

服务启动时会自动执行各自目录下的 Flyway 迁移：

- `backend/auth-service/src/main/resources/db/migration`
- `backend/appointment-service/src/main/resources/db/migration`
- `backend/medical-record-service/src/main/resources/db/migration`

## 当前迁移内容

- 用户账号表：`auth.user_account`
- 号源表：`appointment.slot_inventory`
- 挂号记录表：`appointment.appointment`
- 电子病历表：`medical_record.medical_record`

