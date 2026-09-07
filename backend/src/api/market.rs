use axum::{
    Json,
    extract::{Path, State},
};
use serde::Serialize;

use crate::{
    AppState,
    error::AppError,
    model::{MarketReaction, MarketSnapshot},
};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MarketResponse {
    snapshots: Vec<MarketSnapshot>,
    reactions: Vec<MarketReaction>,
}

pub async fn market(
    State(state): State<AppState>,
    Path(id): Path<i64>,
) -> Result<Json<MarketResponse>, AppError> {
    state.events.get(id).await?;
    Ok(Json(MarketResponse {
        snapshots: state.market.snapshots(id).await?,
        reactions: state.market.reactions(id).await?,
    }))
}
