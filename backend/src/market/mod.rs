mod binance;
mod provider;
mod yahoo;

pub use binance::BinanceProvider;
pub use provider::MarketDataProvider;
pub use yahoo::YahooProvider;

use std::{collections::HashMap, sync::Arc};

use crate::{
    error::AppError,
    model::{MarketSymbol, Quote},
    repository::MarketRepository,
};

pub struct MarketService {
    providers: HashMap<MarketSymbol, Arc<dyn MarketDataProvider>>,
    repository: MarketRepository,
    symbols: Vec<MarketSymbol>,
}

impl MarketService {
    pub fn new(
        yahoo: Arc<dyn MarketDataProvider>,
        binance: Arc<dyn MarketDataProvider>,
        repository: MarketRepository,
        symbols: Vec<MarketSymbol>,
    ) -> Self {
        let providers = MarketSymbol::ALL
            .into_iter()
            .map(|symbol| {
                let provider = if symbol.is_crypto() {
                    binance.clone()
                } else {
                    yahoo.clone()
                };
                (symbol, provider)
            })
            .collect();
        Self {
            providers,
            repository,
            symbols,
        }
    }

    pub fn symbols(&self) -> &[MarketSymbol] {
        &self.symbols
    }

    pub async fn historical_candles(
        &self,
        symbol: MarketSymbol,
        start: chrono::DateTime<chrono::Utc>,
        end: chrono::DateTime<chrono::Utc>,
        interval: crate::model::Interval,
    ) -> Result<Vec<crate::model::Candle>, AppError> {
        self.providers
            .get(&symbol)
            .ok_or_else(|| AppError::Provider(format!("no provider for {symbol}")))?
            .candles(symbol, start, end, interval)
            .await
    }

    pub async fn quote(&self, symbol: MarketSymbol) -> Result<Quote, AppError> {
        self.providers
            .get(&symbol)
            .ok_or_else(|| AppError::Provider(format!("no provider for {symbol}")))?
            .quote(symbol)
            .await
    }

    pub async fn collect_for_event(&self, event_id: i64) -> Result<usize, AppError> {
        let mut saved = 0;
        for symbol in &self.symbols {
            match self.quote(*symbol).await {
                Ok(quote) => {
                    self.repository.save_quote(event_id, &quote).await?;
                    saved += 1;
                }
                Err(error) => {
                    tracing::warn!(event_id, symbol = %symbol, error = %error, "market quote failed");
                }
            }
        }
        Ok(saved)
    }
}
