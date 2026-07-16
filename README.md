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

## 启动方式

项目支持远程服务器访问和本地部署两种启动方式。

### 方式一：远程部署访问

如果远程服务器处于运行状态，可以直接访问线上演示环境：

```text
http://101.200.242.70/login
```

该地址为 PC Web 登录页，适合快速演示和验收。

### 方式二：本地部署启动

本地部署适合开发、联调和验收前自测。推荐使用仓库根目录的一键启动脚本，它会启动 Docker 基础设施、构建并启动后端微服务、启动 AI 服务、PC Web 和患者端小程序开发构建任务。

#### 本地环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+ 与 npm
- Python 3.10+
- Docker Desktop / Docker Engine + Docker Compose
- 可选：微信开发者工具，用于打开患者端小程序产物

#### Windows 一键启动

在仓库根目录执行：

```powershell
.\start-all.ps1 -InstallFrontendDeps -InstallMiniappDeps
```

首次启动建议带上 `-InstallFrontendDeps` 和 `-InstallMiniappDeps` 安装前端依赖；依赖已安装后可直接执行：

```powershell
.\start-all.ps1
```

脚本默认使用本地数据库配置：

- 数据库地址：`localhost:5432`
- 数据库名：`cloud_brain_medical`
- 用户名/密码：`cloudbrain` / `cloudbrain`

如需指定数据库连接信息：

```powershell
.\start-all.ps1 -DbHost localhost -DbName cloud_brain_medical -DbUser cloudbrain -DbPassword cloudbrain
```

启动后常用访问地址：

- PC Web：`http://localhost:5173`
- 网关服务：`http://localhost:8080`
- AI 健康检查：`http://localhost:8000/health`
- DICOM/CT AI 健康检查：`http://localhost:8000/api/ai/dicom/health`
- MinIO 控制台：`http://localhost:9001`
- RabbitMQ 控制台：`http://localhost:15672`

所有启动日志会写入 `logs/` 目录，例如 `logs/frontend.out.log`、`logs/gateway-service.out.log`、`logs/ai-service.out.log`。

#### Linux/服务器部署

服务器部署脚本位于仓库根目录：

```bash
bash start-all.sh
```

该脚本面向服务器环境，会启动 Docker 基础设施、构建后端、启动各 Spring Boot 服务、构建前端并重启 Nginx。脚本中的服务器数据库、Nginx、FRP 等配置需要与实际服务器环境保持一致。

#### 手动分模块启动

如需单独调试某个模块，可按以下顺序启动：

```powershell
docker compose -f docker/docker-compose.yml up -d
```

```powershell
cd backend
mvn -DskipTests package
```

后端各服务可以通过生成的 JAR 单独运行，网关默认端口为 `8080`，各业务服务端口见对应模块的 `application.yml`。

AI 服务：

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
$env:PYTHONPATH=(Resolve-Path .).Path
.\.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

PC Web：

```powershell
cd frontend
npm install
npm run dev -- --host 0.0.0.0
```

患者端微信小程序：

```powershell
cd patient-miniapp
npm install
npm run dev:mp-weixin
```

小程序开发产物生成在 `patient-miniapp/dist/dev/mp-weixin`，可使用微信开发者工具打开。

## 数据库

核心仓储使用 PostgreSQL + Flyway；账号、挂号、号源、支付退款、可靠事件、电子病历和统一医技医嘱均通过增量迁移维护，详见 `docs/database.md`。

## 后端服务边界

- `doctor-service`：科室、医生、排班。
- `appointment-service`：挂号、号源、门诊队列和业务事件。
- `cashier-service`：支付、退款和窗口费用查询。
- `medical-order-service`：检查、检验、处置统一医嘱。
- `pharmacy-service`：处方、药房待发药队列、发药退药、库存扣减回补和库存流水。
- `report-service`、`audit-service`：正式报告和审计能力。

## D9-D10 验证

- 后端：`cd backend && mvn test`
- 工作人员端：`cd frontend && npm run build`
- 患者端微信小程序：`cd patient-miniapp && npm run build:mp-weixin`
- AI 服务语法：`python -m compileall ai-service/app`
- AI 服务测试：`$env:PYTHONPATH=(Resolve-Path ai-service).Path; python -m pytest ai-service/tests`
