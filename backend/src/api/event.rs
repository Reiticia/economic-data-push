use axum::{
    Json,
    extract::{Path, Query, State},
};
use serde::{Deserialize, Serialize};

use crate::{
    AppState,
    error::AppError,
    model::{AnalysisReport, EconomicEvent, EventObservation},
};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EventDetail {
    event: EconomicEvent,
    observations: Vec<EventObservation>,
}

pub async fn detail(
    State(state): State<AppState>,
    Path(id): Path<i64>,
) -> Result<Json<EventDetail>, AppError> {
    let event = state.events.get(id).await?;
    let observations = state.events.observations(id).await?;
    Ok(Json(EventDetail {
        event,
        observations,
    }))
}

pub async fn analysis(
    State(state): State<AppState>,
    Path(id): Path<i64>,
) -> Result<Json<AnalysisReport>, AppError> {
    Ok(Json(state.analyses.get(id).await?))
}

#[derive(Debug, Deserialize)]
pub struct HistoryQuery {
    country: Option<String>,
    category: Option<String>,
    limit: Option<u32>,
}

pub async fn history(
    State(state): State<AppState>,
    Query(query): Query<HistoryQuery>,
) -> Result<Json<Vec<EconomicEvent>>, AppError> {
    Ok(Json(
        state
            .events
            .history(
                query.country.as_deref(),
                query.category.as_deref(),
                query.limit.unwrap_or(100),
            )
            .await?,
    ))
}
