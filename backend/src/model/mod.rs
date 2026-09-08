mod analysis;
mod event;
mod historical;
mod market;
mod observation;
pub use historical::*;

pub use analysis::*;
pub use event::*;
pub use market::*;
pub use observation::*;

use serde::Serialize;

#[derive(Clone, Debug, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum AppEvent {
    EconomicEventReleased {
        event_id: i64,
        event: String,
        actual: Option<String>,
        consensus: Option<String>,
    },
    MarketDataCollected {
        event_id: i64,
    },
    AnalysisCompleted {
        event_id: i64,
    },
}
