mod provider;
pub mod trading_economics;
pub mod trading_economics_api;
pub use trading_economics_api::TradingEconomicsApiProvider;

pub use provider::CalendarProvider;
pub use trading_economics::TradingEconomicsProvider;

use std::sync::Arc;

use chrono::{DateTime, Utc};

use crate::{
    error::AppError, model::EconomicEvent, repository::EventRepository,
    translation::TranslationService,
};

pub struct CalendarService {
    provider: Arc<dyn CalendarProvider>,
    events: EventRepository,
    translation: Option<Arc<TranslationService>>,
}

impl CalendarService {
    pub fn new(provider: Arc<dyn CalendarProvider>, events: EventRepository) -> Self {
        Self {
            provider,
            events,
            translation: None,
        }
    }

    pub fn with_translation(mut self, translation: Arc<TranslationService>) -> Self {
        self.translation = Some(translation);
        self
    }

    pub async fn sync(&self, start: DateTime<Utc>, end: DateTime<Utc>) -> Result<usize, AppError> {
        let mut events = self.provider.fetch_events(start, end).await?;
        let count = events.len();
        if let Some(translation) = &self.translation
            && let Err(error) = translation.enrich(&mut events).await
        {
            tracing::warn!(%error, "event-name translation failed; saving source names");
        }
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
