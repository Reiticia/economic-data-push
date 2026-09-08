mod calendar;
mod event;
mod market;
mod websocket;

use axum::{Router, routing::get};
use tower_http::{cors::CorsLayer, trace::TraceLayer};

use crate::AppState;

pub fn router(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/api/v1/events/upcoming", get(calendar::upcoming))
        .route("/api/v1/calendar", get(calendar::calendar))
        .route("/api/v1/events/history", get(event::history))
        .route("/api/v1/history/backfill", get(backfill_status))
        .route("/api/v1/events/{id}", get(event::detail))
        .route("/api/v1/events/{id}/analysis", get(event::analysis))
        .route("/api/v1/events/{id}/market", get(market::market))
        .route("/api/v1/ws", get(websocket::websocket))
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

async fn backfill_status(
    axum::extract::State(state): axum::extract::State<AppState>,
) -> Result<axum::Json<Option<crate::backfill::repository::BackfillSummary>>, crate::error::AppError>
{
    Ok(axum::Json(state.backfill.latest().await?))
}

async fn health() -> &'static str {
    "ok"
}
