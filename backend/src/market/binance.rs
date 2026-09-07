use std::str::FromStr;

use async_trait::async_trait;
use chrono::{DateTime, TimeZone, Utc};
use serde::Deserialize;

use crate::{
    error::AppError,
    market::MarketDataProvider,
    model::{Candle, Interval, MarketSymbol, Quote},
};

pub struct BinanceProvider {
    client: reqwest::Client,
    base_url: String,
}

impl BinanceProvider {
    pub fn new(client: reqwest::Client, base_url: impl Into<String>) -> Self {
        Self {
            client,
            base_url: base_url.into().trim_end_matches('/').to_owned(),
        }
    }

    fn ticker(symbol: MarketSymbol) -> Result<&'static str, AppError> {
        match symbol {
            MarketSymbol::Bitcoin => Ok("BTCUSDT"),
            MarketSymbol::Ethereum => Ok("ETHUSDT"),
            _ => Err(AppError::Provider(format!(
                "{symbol} does not belong to Binance"
            ))),
        }
    }
}

#[async_trait]
impl MarketDataProvider for BinanceProvider {
    async fn quote(&self, symbol: MarketSymbol) -> Result<Quote, AppError> {
        let ticker = Self::ticker(symbol)?;
        let response: BinanceTicker = self
            .client
            .get(format!("{}/ticker/price", self.base_url))
            .query(&[("symbol", ticker)])
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;
        let price = f64::from_str(&response.price)
            .map_err(|error| AppError::Provider(format!("invalid Binance price: {error}")))?;
        Ok(Quote {
            symbol,
            timestamp: Utc::now(),
            price,
        })
    }

    async fn candles(
        &self,
        symbol: MarketSymbol,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
        interval: Interval,
    ) -> Result<Vec<Candle>, AppError> {
        let ticker = Self::ticker(symbol)?;
        let interval = match interval {
            Interval::OneMinute => "1m",
            Interval::FiveMinutes => "5m",
            Interval::FifteenMinutes => "15m",
            Interval::OneHour => "1h",
            Interval::OneDay => "1d",
        };
        let values: Vec<Vec<serde_json::Value>> = self
            .client
            .get(format!("{}/klines", self.base_url))
            .query(&[
                ("symbol", ticker.to_owned()),
                ("interval", interval.to_owned()),
                ("startTime", start.timestamp_millis().to_string()),
                ("endTime", end.timestamp_millis().to_string()),
                ("limit", "1000".to_owned()),
            ])
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;

        let candles = values
            .into_iter()
            .filter_map(|row| {
                let timestamp = row
                    .first()?
                    .as_i64()
                    .and_then(|value| Utc.timestamp_millis_opt(value).single())?;
                let number = |index: usize| row.get(index)?.as_str()?.parse::<f64>().ok();
                Some(Candle {
                    symbol,
                    timestamp,
                    open: number(1)?,
                    high: number(2)?,
                    low: number(3)?,
                    close: number(4)?,
                    volume: number(5),
                })
            })
            .collect::<Vec<_>>();
        Ok(candles)
    }
}

#[derive(Debug, Deserialize)]
struct BinanceTicker {
    price: String,
}
