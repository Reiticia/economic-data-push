pub mod analysis;
pub mod api;
pub mod calendar;
pub mod config;
pub mod error;
pub mod market;
pub mod model;
pub mod repository;
pub mod scheduler;

use std::sync::Arc;

use analysis::AnalysisService;
use calendar::CalendarService;
use market::MarketService;
use repository::{AnalysisRepository, EventRepository, MarketRepository};
use tokio::sync::broadcast;

use crate::model::AppEvent;

#[derive(Clone)]
pub struct AppState {
    pub events: EventRepository,
    pub market: MarketRepository,
    pub analyses: AnalysisRepository,
    pub calendar_service: Arc<CalendarService>,
    pub market_service: Arc<MarketService>,
    pub analysis_service: Arc<AnalysisService>,
    pub event_bus: broadcast::Sender<AppEvent>,
}
