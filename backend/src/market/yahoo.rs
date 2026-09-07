use async_trait::async_trait;
use chrono::{DateTime, TimeZone, Utc};
use serde::Deserialize;

use crate::{
    error::AppError,
    market::MarketDataProvider,
    model::{Candle, Interval, MarketSymbol, Quote},
};

pub struct YahooProvider {
    client: reqwest::Client,
    base_url: String,
}

impl YahooProvider {
    pub fn new(client: reqwest::Client, base_url: impl Into<String>) -> Self {
        Self {
            client,
            base_url: base_url.into().trim_end_matches('/').to_owned(),
        }
    }

    fn ticker(symbol: MarketSymbol) -> Result<&'static str, AppError> {
        match symbol {
            MarketSymbol::Gold => Ok("GC=F"),
            MarketSymbol::Silver => Ok("SI=F"),
            MarketSymbol::Sp500 => Ok("^GSPC"),
            MarketSymbol::Nasdaq100 => Ok("^NDX"),
            MarketSymbol::DowJones => Ok("^DJI"),
            MarketSymbol::Us2y => Ok("^UST2YR"),
            MarketSymbol::Us10y => Ok("^TNX"),
            MarketSymbol::Dxy => Ok("DX-Y.NYB"),
            MarketSymbol::EurUsd => Ok("EURUSD=X"),
            MarketSymbol::GbpUsd => Ok("GBPUSD=X"),
            MarketSymbol::UsdJpy => Ok("JPY=X"),
            MarketSymbol::AudUsd => Ok("AUDUSD=X"),
            MarketSymbol::Wti => Ok("CL=F"),
            MarketSymbol::Brent => Ok("BZ=F"),
            MarketSymbol::NaturalGas => Ok("NG=F"),
            MarketSymbol::Bitcoin | MarketSymbol::Ethereum => {
                Err(AppError::Provider(format!("{symbol} belongs to Binance")))
            }
        }
    }

    async fn chart(
        &self,
        symbol: MarketSymbol,
        parameters: &[(&str, String)],
    ) -> Result<YahooChartResult, AppError> {
        let ticker = Self::ticker(symbol)?;
        let url = format!("{}/{}", self.base_url, ticker);
        let response: YahooResponse = self
            .client
            .get(url)
            .query(parameters)
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;
        response
            .chart
            .result
            .and_then(|mut results| results.pop())
            .ok_or_else(|| {
                let detail = response
                    .chart
                    .error
                    .map(|error| format!("{}: {}", error.code, error.description))
                    .unwrap_or_else(|| "empty chart response".to_owned());
                AppError::Provider(format!("Yahoo {ticker}: {detail}"))
            })
    }
}

#[async_trait]
impl MarketDataProvider for YahooProvider {
    async fn quote(&self, symbol: MarketSymbol) -> Result<Quote, AppError> {
        let result = self
            .chart(
                symbol,
                &[("interval", "1m".to_owned()), ("range", "1d".to_owned())],
            )
            .await?;
        let price = result
            .meta
            .regular_market_price
            .or_else(|| {
                result
                    .indicators
                    .quote
                    .first()
                    .and_then(|quotes| quotes.close.iter().rev().flatten().next().copied())
            })
            .ok_or_else(|| AppError::Provider(format!("Yahoo returned no price for {symbol}")))?;
        let timestamp = result
            .meta
            .regular_market_time
            .and_then(|timestamp| Utc.timestamp_opt(timestamp, 0).single())
            .unwrap_or_else(Utc::now);
        Ok(Quote {
            symbol,
            timestamp,
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
        let interval = match interval {
            Interval::OneMinute => "1m",
            Interval::FiveMinutes => "5m",
            Interval::FifteenMinutes => "15m",
            Interval::OneHour => "1h",
            Interval::OneDay => "1d",
        };
        let result = self
            .chart(
                symbol,
                &[
                    ("interval", interval.to_owned()),
                    ("period1", start.timestamp().to_string()),
                    ("period2", end.timestamp().to_string()),
                ],
            )
            .await?;
        let timestamps = result.timestamp.unwrap_or_default();
        let Some(values) = result.indicators.quote.first() else {
            return Ok(Vec::new());
        };
        let mut candles = Vec::new();
        for (index, timestamp) in timestamps.into_iter().enumerate() {
            let Some(timestamp) = Utc.timestamp_opt(timestamp, 0).single() else {
                continue;
            };
            let (Some(open), Some(high), Some(low), Some(close)) = (
                value_at(&values.open, index),
                value_at(&values.high, index),
                value_at(&values.low, index),
                value_at(&values.close, index),
            ) else {
                continue;
            };
            candles.push(Candle {
                symbol,
                timestamp,
                open,
                high,
                low,
                close,
                volume: value_at(&values.volume, index),
            });
        }
        Ok(candles)
    }
}

fn value_at(values: &[Option<f64>], index: usize) -> Option<f64> {
    values.get(index).copied().flatten()
}

#[derive(Debug, Deserialize)]
struct YahooResponse {
    chart: YahooChart,
}

#[derive(Debug, Deserialize)]
struct YahooChart {
    result: Option<Vec<YahooChartResult>>,
    error: Option<YahooError>,
}

#[derive(Debug, Deserialize)]
struct YahooError {
    code: String,
    description: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct YahooChartResult {
    meta: YahooMeta,
    timestamp: Option<Vec<i64>>,
    indicators: YahooIndicators,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct YahooMeta {
    regular_market_price: Option<f64>,
    regular_market_time: Option<i64>,
}

#[derive(Debug, Deserialize)]
struct YahooIndicators {
    quote: Vec<YahooQuoteValues>,
}

#[derive(Debug, Deserialize)]
struct YahooQuoteValues {
    #[serde(default)]
    open: Vec<Option<f64>>,
    #[serde(default)]
    high: Vec<Option<f64>>,
    #[serde(default)]
    low: Vec<Option<f64>>,
    #[serde(default)]
    close: Vec<Option<f64>>,
    #[serde(default)]
    volume: Vec<Option<f64>>,
}
