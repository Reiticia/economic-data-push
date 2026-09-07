mod market_reaction;
mod report;
mod rule_engine;
mod surprise;

pub use market_reaction::calculate_reactions;
pub use rule_engine::RuleEngine;
pub use surprise::raw_surprise;

use chrono::Utc;

use crate::{
    error::AppError,
    model::AnalysisReport,
    repository::{AnalysisRepository, EventRepository, MarketRepository},
};

pub struct AnalysisService {
    events: EventRepository,
    market: MarketRepository,
    analyses: AnalysisRepository,
    rules: RuleEngine,
}

impl AnalysisService {
    pub fn new(
        events: EventRepository,
        market: MarketRepository,
        analyses: AnalysisRepository,
        rules: RuleEngine,
    ) -> Self {
        Self {
            events,
            market,
            analyses,
            rules,
        }
    }

    pub async fn analyze(&self, event_id: i64) -> Result<AnalysisReport, AppError> {
        let event = self.events.get(event_id).await?;
        let surprise = raw_surprise(event.actual, event.consensus);
        let signal = self.rules.signal_for(&event, surprise);
        let expected = self.rules.expected_reactions(signal);
        let snapshots = self.market.snapshots(event_id).await?;
        let observed = calculate_reactions(event_id, event.event_time, &snapshots);
        for reaction in &observed {
            self.market.upsert_reaction(reaction).await?;
        }
        let comparisons = report::compare(&expected, &observed);
        let summary = report::summary(&event, surprise, signal, &comparisons);
        let now = Utc::now();
        let report = AnalysisReport {
            id: 0,
            event_id,
            raw_surprise: surprise,
            macro_signal: signal,
            expected_reactions: expected,
            observed_reactions: observed,
            comparisons,
            summary,
            created_at: now,
            updated_at: now,
        };
        self.analyses.save(&report).await?;
        self.analyses.get(event_id).await
    }
}
