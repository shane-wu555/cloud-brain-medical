# AI 药房库存预测

## 功能概览

药房库存模块内置了药品价值-波动性九宫格分类机制。系统会结合药品单价、历史出库波动、发药频次等指标，对药品进行分层，并为不同类型药品匹配不同的库存阈值计算策略。

- 高波动药品：采用 `XGBoost + Prophet` 集成预测路线
- 中波动药品：采用轻量趋势预测
- 低波动药品：采用规则化安全库存计算

现有药品目录的库存预警阈值由 Flyway 迁移 `V7__seed_ai_inventory_thresholds.sql` 初始化，前端库存页可直接使用这些阈值进行预警展示。

## 后端能力

药房服务已经提供库存预测预览接口：

```http
GET /api/drugs/inventory-forecast?lookbackDays=90
```

返回内容包含：

- 药品基础信息
- 当前库存与预警阈值
- 推荐预警阈值
- 药品价值层级
- 波动层级
- 预测模型路线

库存阈值的定时刷新由配置项控制：

```powershell
$env:INVENTORY_AI_FORECAST_ENABLED="true"
```

## 验证数据集

药品需求预测验证数据集已纳入项目目录：

```text
ai-service/training/inventory_forecast/data/Hospital Medication Demand.csv
```

数据列格式：

```csv
article_id,date,total_quantity
```

## 验证脚本

安装验证依赖：

```powershell
pip install -r ai-service/requirements-forecast.txt
```

运行验证：

```powershell
python ai-service/training/inventory_forecast/validate_medication_demand_forecast.py `
  --output docs/generated/medication-demand-forecast-report.json `
  --metrics-csv docs/generated/medication-demand-forecast-metrics.csv
```

如果需要替换数据源，也可以显式指定：

```powershell
python ai-service/training/inventory_forecast/validate_medication_demand_forecast.py `
  --csv "自定义路径\\Hospital Medication Demand.csv"
```

## 演示口径

可以直接这样介绍：

```text
系统内置药品价值-波动性九宫格分类机制。高波动药品采用 XGBoost + Prophet 集成预测，中低波动药品采用轻量趋势与规则化库存策略，兼顾预测精度与计算效率。库存阈值支持定时刷新，也支持结合药房业务策略进行统一配置。我们使用项目内置的药品需求验证数据集对预测路线进行了验证，并可在现有框架上继续扩展训练与调优。
```
