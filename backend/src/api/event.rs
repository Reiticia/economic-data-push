use axum::{
    Json,
    extract::{Path, Query, State},
};
use chrono::{Duration, NaiveDate};
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
    offset: Option<u32>,
    from: Option<NaiveDate>,
    to: Option<NaiveDate>,
}

pub async fn history(
    State(state): State<AppState>,
    Query(query): Query<HistoryQuery>,
) -> Result<Json<Vec<EconomicEvent>>, AppError> {
    if matches!((query.from, query.to), (Some(from), Some(to)) if to < from || to - from >= Duration::days(93))
    {
        return Err(AppError::InvalidRequest(
            "history date range must be ordered and at most 93 days".into(),
        ));
    }
    let from = query
        .from
        .map(|d| d.and_hms_opt(0, 0, 0).unwrap().and_utc());
    let to = query
        .to
        .map(|d| {
            d.succ_opt()
                .ok_or_else(|| AppError::InvalidRequest("date overflow".into()))
        })
        .transpose()?
        .map(|d| d.and_hms_opt(0, 0, 0).unwrap().and_utc());
    Ok(Json(
        state
            .events
            .history_page(
                query.country.as_deref(),
                query.category.as_deref(),
                query.limit.unwrap_or(100),
                query.offset.unwrap_or(0),
                from,
                to,
            )
            .await?,
    ))
}
