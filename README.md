# 智慧云脑诊疗平台

本仓库根据统一后的需求和技术选型持续实现。患者使用微信小程序，医院工作人员使用按角色授权的 PC Web，核心业务数据存储于 PostgreSQL。

## 技术栈

- 患者端：uni-app、Vue 3、TypeScript，发布为微信小程序
- 工作人员端：Vue 3、TypeScript、Pinia、Vue Router、Axios、Element Plus、ECharts
- 后端：Spring Boot 3、Java 17、Spring Cloud Gateway、Spring Security、JWT、PostgreSQL、Redis、RabbitMQ、MinIO、Elasticsearch
- AI 服务：FastAPI、LangChain、LangGraph、PyTorch、Transformers、MONAI、pgvector
- 基础设施：Docker Compose、Nginx、Kubernetes、Prometheus、Grafana、OpenTelemetry、Jaeger

## 目录

- `patient-miniapp/`：患者微信小程序
- `frontend/`：医院工作人员 PC Web
- `backend/`：Spring Boot 微服务集合
- `ai-service/`：FastAPI AI 智能服务
- `docker/`：本地开发与反向代理配置
- `k8s/`：Kubernetes 部署清单占位
- `scripts/`：数据库初始化、迁移和 AI 工具脚本
- `docs/`：项目设计文档

## 数据库

核心仓储使用 PostgreSQL + Flyway；账号、挂号、号源、支付退款、可靠事件、电子病历和统一医技医嘱均通过增量迁移维护，详见 `docs/database.md`。

## 后端服务边界

- `doctor-service`：科室、医生、排班。
- `appointment-service`：挂号、号源、门诊队列和业务事件。
- `cashier-service`：支付、退款和窗口费用查询。
- `medical-order-service`：检查、检验、处置统一医嘱。
- `report-service`、`pharmacy-service`、`audit-service`：正式报告、药房和审计能力。
