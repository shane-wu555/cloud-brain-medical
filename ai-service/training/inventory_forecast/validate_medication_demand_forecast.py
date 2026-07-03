"""药品需求预测验证脚本。"""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import math
import os
import warnings
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


REQUIRED_COLUMNS = {"article_id", "date", "total_quantity"}
DEFAULT_DATASET = Path(__file__).resolve().parent / "data" / "Hospital Medication Demand.csv"
pd = None
mean_absolute_error = None
mean_squared_error = None


@dataclass
class ArticleMetrics:
    article_id: str
    volatility_tier: str
    train_days: int
    test_days: int
    average_daily_demand: float
    coefficient_of_variation: float
    prophet_mae: float
    xgboost_mae: float
    ensemble_mae: float
    moving_average_mae: float
    ensemble_rmse: float
    ensemble_smape: float


def main() -> int:
    args = parse_args()
    ensure_forecast_dependencies()

    csv_value = args.csv or os.environ.get("MEDICATION_DEMAND_CSV")
    csv_path = Path(csv_value) if csv_value else DEFAULT_DATASET
    if not csv_path.exists():
        raise SystemExit(f"未找到 CSV 文件: {csv_path}")

    data = load_dataset(csv_path)
    article_ids = top_articles(data, args.max_articles, args.min_days)
    if not article_ids:
        raise SystemExit("没有满足验证条件的药品时间序列。")

    metrics = []
    for article_id in article_ids:
        series = article_series(data, article_id)
        if len(series) < args.horizon + args.min_days:
            continue
        metrics.append(validate_article(series, article_id, args.horizon))

    if not metrics:
        raise SystemExit("训练集/验证集切分后没有可用样本。")

    report = build_report(csv_path, args, metrics)
    print(json.dumps(report, ensure_ascii=False, indent=2))

    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    if args.metrics_csv:
        metrics_path = Path(args.metrics_csv)
        metrics_path.parent.mkdir(parents=True, exist_ok=True)
        pd.DataFrame([asdict(item) for item in metrics]).to_csv(metrics_path, index=False)

    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="验证 XGBoost + Prophet 药品需求预测效果。")
    parser.add_argument("--csv", help=f"药品需求 CSV 路径，默认使用: {DEFAULT_DATASET}")
    parser.add_argument("--max-articles", type=int, default=30, help="参与验证的高需求药品数量。")
    parser.add_argument("--min-days", type=int, default=120, help="每个药品最少需要的历史天数。")
    parser.add_argument("--horizon", type=int, default=28, help="作为验证集保留的末尾天数。")
    parser.add_argument("--output", help="可选的 JSON 报告输出路径。")
    parser.add_argument("--metrics-csv", help="可选的逐药品指标 CSV 输出路径。")
    return parser.parse_args()


def ensure_forecast_dependencies() -> None:
    global pd, mean_absolute_error, mean_squared_error
    missing = []
    try:
        with quiet_import():
            import pandas as pandas_module

            pd = pandas_module
    except Exception as exc:
        missing.append(f"pandas ({exc})")
    try:
        with quiet_import():
            from sklearn.metrics import mean_absolute_error as mae
            from sklearn.metrics import mean_squared_error as mse

            mean_absolute_error = mae
            mean_squared_error = mse
    except Exception as exc:
        missing.append(f"scikit-learn ({exc})")
    try:
        with quiet_import():
            import xgboost  # noqa: F401
    except Exception as exc:
        missing.append(f"xgboost ({exc})")
    try:
        with quiet_import():
            import prophet  # noqa: F401
    except Exception as exc:
        missing.append(f"prophet ({exc})")
    if missing:
        raise SystemExit(
            "缺少预测验证依赖: "
            f"{'; '.join(missing)}. Install or repair them with "
            "`pip install -r ai-service/requirements-forecast.txt`."
        )


@contextlib.contextmanager
def quiet_import():
    stderr = io.StringIO()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        with contextlib.redirect_stderr(stderr):
            yield


def load_dataset(csv_path: Path) -> pd.DataFrame:
    data = pd.read_csv(csv_path)
    missing_columns = REQUIRED_COLUMNS - set(data.columns)
    if missing_columns:
        raise SystemExit(f"CSV 缺少必要列: {sorted(missing_columns)}")
    data = data.loc[:, ["article_id", "date", "total_quantity"]].copy()
    data["date"] = pd.to_datetime(data["date"], errors="coerce")
    data["total_quantity"] = pd.to_numeric(data["total_quantity"], errors="coerce").fillna(0)
    data = data.dropna(subset=["article_id", "date"])
    data = data.groupby(["article_id", "date"], as_index=False)["total_quantity"].sum()
    return data.sort_values(["article_id", "date"])


def top_articles(data: pd.DataFrame, max_articles: int, min_days: int) -> list[str]:
    summary = (
        data.groupby("article_id")
        .agg(total_quantity=("total_quantity", "sum"), days=("date", "nunique"))
        .query("days >= @min_days")
        .sort_values("total_quantity", ascending=False)
    )
    return summary.head(max_articles).index.astype(str).tolist()


def article_series(data: pd.DataFrame, article_id: str) -> pd.DataFrame:
    series = data.loc[data["article_id"] == article_id, ["date", "total_quantity"]].copy()
    full_index = pd.date_range(series["date"].min(), series["date"].max(), freq="D")
    series = (
        series.set_index("date")
        .reindex(full_index, fill_value=0)
        .rename_axis("date")
        .reset_index()
        .rename(columns={"total_quantity": "y"})
    )
    return series


def validate_article(series: pd.DataFrame, article_id: str, horizon: int) -> ArticleMetrics:
    from prophet import Prophet
    from xgboost import XGBRegressor

    train = series.iloc[:-horizon].copy()
    test = series.iloc[-horizon:].copy()

    prophet_model = Prophet(
        yearly_seasonality=True,
        weekly_seasonality=True,
        daily_seasonality=False,
        seasonality_mode="additive",
    )
    prophet_model.fit(train.rename(columns={"date": "ds"})[["ds", "y"]])
    future = pd.DataFrame({"ds": test["date"]})
    prophet_pred = prophet_model.predict(future)["yhat"].clip(lower=0).to_numpy()

    xgb_train = build_xgboost_training_frame(train)
    xgb_model = XGBRegressor(
        n_estimators=250,
        max_depth=4,
        learning_rate=0.05,
        subsample=0.9,
        colsample_bytree=0.9,
        objective="reg:squarederror",
        random_state=42,
    )
    feature_columns = ["day_index", "day_of_week", "month",
                       "lag_1", "lag_7", "lag_14", "lag_28",
                       "rolling_7", "rolling_14", "rolling_28", "trend_7"]
    xgb_model.fit(xgb_train[feature_columns], xgb_train["y"])
    xgb_pred = recursive_xgboost_forecast(xgb_model, train, test["date"], feature_columns)

    ensemble_pred = (prophet_pred * 0.45) + (xgb_pred * 0.45) + (moving_average_forecast(train, horizon) * 0.10)
    baseline_pred = moving_average_forecast(train, horizon)
    actual = test["y"].to_numpy()
    average_demand = float(train["y"].mean())
    std_demand = float(train["y"].std(ddof=0))

    return ArticleMetrics(
        article_id=article_id,
        volatility_tier=volatility_tier(average_demand, std_demand),
        train_days=len(train),
        test_days=len(test),
        average_daily_demand=round(average_demand, 4),
        coefficient_of_variation=round(coefficient_of_variation(average_demand, std_demand), 4),
        prophet_mae=round(float(mean_absolute_error(actual, prophet_pred)), 4),
        xgboost_mae=round(float(mean_absolute_error(actual, xgb_pred)), 4),
        ensemble_mae=round(float(mean_absolute_error(actual, ensemble_pred)), 4),
        moving_average_mae=round(float(mean_absolute_error(actual, baseline_pred)), 4),
        ensemble_rmse=round(float(math.sqrt(mean_squared_error(actual, ensemble_pred))), 4),
        ensemble_smape=round(smape(actual, ensemble_pred), 4),
    )


def build_xgboost_training_frame(series: pd.DataFrame) -> pd.DataFrame:
    frame = series.copy()
    start_date = frame["date"].min()
    frame["day_index"] = (frame["date"] - start_date).dt.days
    frame["day_of_week"] = frame["date"].dt.dayofweek
    frame["month"] = frame["date"].dt.month

    # ========== 新增更多滞后特征 ==========
    frame["lag_1"] = frame["y"].shift(1)
    frame["lag_7"] = frame["y"].shift(7)
    frame["lag_14"] = frame["y"].shift(14)   # 新增：前2周
    frame["lag_28"] = frame["y"].shift(28)   # 新增：前4周（月度周期）

    # ========== 新增更多滚动窗口 ==========
    frame["rolling_7"] = frame["y"].shift(1).rolling(7).mean()
    frame["rolling_14"] = frame["y"].shift(1).rolling(14).mean()  # 新增
    frame["rolling_28"] = frame["y"].shift(1).rolling(28).mean()

    # 新增：销量变化的趋势（最近7天相对于前7天的变化率）
    frame["trend_7"] = (frame["rolling_7"] - frame["rolling_14"]) / (frame["rolling_14"] + 1)

    return frame.dropna()


def recursive_xgboost_forecast(model, train: pd.DataFrame, dates: Iterable[pd.Timestamp], feature_columns: list[str]):
    history = train["y"].astype(float).tolist()
    start_date = train["date"].min()
    predictions = []
    for forecast_date in dates:
        features = pd.DataFrame(
            [
                {
                    "day_index": (forecast_date - start_date).days,
                    "day_of_week": forecast_date.dayofweek,
                    "month": forecast_date.month,
                    "lag_1": history[-1],
                    "lag_7": history[-7] if len(history) >= 7 else history[-1],
                    "rolling_7": average(history[-7:]),
                    "rolling_28": average(history[-28:]),
                    "lag_14": history[-14] if len(history) >= 14 else history[-1],
                    "lag_28": history[-28] if len(history) >= 28 else history[-1],
                    "rolling_14": average(history[-14:]),
                    "trend_7": (average(history[-7:]) - average(history[-14:])) / (average(history[-14:]) + 1)
                }
            ]
        )
        predicted = max(0.0, float(model.predict(features[feature_columns])[0]))
        predictions.append(predicted)
        history.append(predicted)
    return pd.Series(predictions).to_numpy()


def moving_average_forecast(train: pd.DataFrame, horizon: int):
    demand = train["y"].astype(float).tolist()
    value = average(demand[-28:] or demand)
    return pd.Series([value] * horizon).to_numpy()


def build_report(csv_path: Path, args: argparse.Namespace, metrics: list[ArticleMetrics]) -> dict:
    high_volatility = [item for item in metrics if item.volatility_tier == "HIGH"]
    evaluated = high_volatility or metrics
    ensemble_better_count = sum(item.ensemble_mae <= item.moving_average_mae for item in evaluated)
    return {
        "dataset": str(csv_path),
        "validationPurpose": "XGBoost + Prophet 药品需求预测验证",
        "inventoryStrategy": "九宫格分类 + 集成预测 + 可配置库存阈值刷新",
        "articlesEvaluated": len(metrics),
        "highVolatilityArticlesEvaluated": len(high_volatility),
        "holdoutDays": args.horizon,
        "metricsScope": "高波动药品" if high_volatility else "全部参与验证的药品",
        "ensembleBetterOrEqualBaselineCount": ensemble_better_count,
        "averageEnsembleMae": round(average(item.ensemble_mae for item in evaluated), 4),
        "averageMovingAverageMae": round(average(item.moving_average_mae for item in evaluated), 4),
        "items": [asdict(item) for item in metrics],
    }


def volatility_tier(mean: float, std: float) -> str:
    cv = coefficient_of_variation(mean, std)
    if cv >= 0.75:
        return "HIGH"
    if cv >= 0.35:
        return "MEDIUM"
    return "LOW"


def coefficient_of_variation(mean: float, std: float) -> float:
    return 0.0 if mean <= 0 else std / mean


def smape(actual, predicted) -> float:
    total = 0.0
    count = 0
    for left, right in zip(actual, predicted):
        denominator = (abs(float(left)) + abs(float(right))) / 2
        if denominator == 0:
            continue
        total += abs(float(left) - float(right)) / denominator
        count += 1
    return 0.0 if count == 0 else total * 100 / count


def average(values: Iterable[float]) -> float:
    values = list(values)
    if not values:
        return 0.0
    return float(sum(values) / len(values))


if __name__ == "__main__":
    raise SystemExit(main())
