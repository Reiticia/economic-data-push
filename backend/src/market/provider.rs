use async_trait::async_trait;
use chrono::{DateTime, Utc};

use crate::{
    error::AppError,
    model::{Candle, Interval, MarketSymbol, Quote},
};

#[async_trait]
pub trait MarketDataProvider: Send + Sync {
    async fn quote(&self, symbol: MarketSymbol) -> Result<Quote, AppError>;

    async fn candles(
        &self,
        symbol: MarketSymbol,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
        interval: Interval,
    ) -> Result<Vec<Candle>, AppError>;
}
