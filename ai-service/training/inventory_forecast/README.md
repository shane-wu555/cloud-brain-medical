# 药品需求预测训练与验证

本目录用于存放 AI 药房库存预测相关的训练、验证和数据集文件。

当前包含：

- `validate_medication_demand_forecast.py`：药品需求预测验证脚本
- `data/Hospital Medication Demand.csv`：药品需求验证数据集

## 依赖安装

```powershell
pip install -r ai-service/requirements-forecast.txt
```

## 运行方式

```powershell
python ai-service/training/inventory_forecast/validate_medication_demand_forecast.py `
  --output docs/generated/medication-demand-forecast-report.json `
  --metrics-csv docs/generated/medication-demand-forecast-metrics.csv
```

## 说明

该目录对应药房库存预测中的高波动药品建模路线，验证脚本会基于 `XGBoost + Prophet` 对药品日需求时序进行评估。后续如果需要补充训练脚本、特征工程脚本、模型导出脚本，也建议继续放在这个目录下统一管理。
