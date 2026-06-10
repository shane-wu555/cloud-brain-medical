# 智慧云脑诊疗平台

本仓库根据《智慧云脑诊疗平台技术选型》搭建基础项目架构，当前只保留工程骨架与少量启动占位代码，便于后续按阶段补充业务实现。

## 技术栈

- 前端：Vue 3、TypeScript、Pinia、Vue Router、Axios、Element Plus、ECharts
- 后端：Spring Boot 3、Java 17、Spring Cloud Gateway、Spring Security、JWT、PostgreSQL、Redis、RabbitMQ、MinIO、Elasticsearch
- AI 服务：FastAPI、LangChain、LangGraph、PyTorch、Transformers、MONAI、pgvector
- 基础设施：Docker Compose、Nginx、Kubernetes、Prometheus、Grafana、OpenTelemetry、Jaeger

## 目录

- `frontend/`：Vue 3 前端工作台
- `backend/`：Spring Boot 微服务集合
- `ai-service/`：FastAPI AI 智能服务
- `docker/`：本地开发与反向代理配置
- `k8s/`：Kubernetes 部署清单占位
- `scripts/`：数据库初始化、迁移和 AI 工具脚本
- `docs/`：项目设计文档

## 数据库

V1 核心仓储已接入 PostgreSQL + Flyway，详见 `docs/database.md`。
