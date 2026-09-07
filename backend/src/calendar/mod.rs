mod provider;
pub mod trading_economics;

pub use provider::CalendarProvider;
pub use trading_economics::TradingEconomicsProvider;

use std::sync::Arc;

use chrono::{DateTime, Utc};

use crate::{error::AppError, model::EconomicEvent, repository::EventRepository};

pub struct CalendarService {
    provider: Arc<dyn CalendarProvider>,
    events: EventRepository,
}

impl CalendarService {
    pub fn new(provider: Arc<dyn CalendarProvider>, events: EventRepository) -> Self {
        Self { provider, events }
    }

    pub async fn sync(&self, start: DateTime<Utc>, end: DateTime<Utc>) -> Result<usize, AppError> {
        let events = self.provider.fetch_events(start, end).await?;
        let count = events.len();
        self.events.save_events(&events).await?;
        Ok(count)
    }

    pub async fn fetch(
        &self,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
    ) -> Result<Vec<EconomicEvent>, AppError> {
        self.provider.fetch_events(start, end).await
    }
}
