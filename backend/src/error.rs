use axum::{
    Json,
    http::StatusCode,
    response::{IntoResponse, Response},
};
use serde_json::json;

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("configuration error: {0}")]
    Config(String),
    #[error("database error: {0}")]
    Database(#[from] sqlx::Error),
    #[error("http provider error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("provider data error: {0}")]
    Provider(String),
    #[error("resource not found")]
    NotFound,
    #[error("invalid request: {0}")]
    InvalidRequest(String),
    #[error("analysis is not available: {0}")]
    AnalysisUnavailable(String),
    #[error("internal error: {0}")]
    Internal(String),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, message) = match &self {
            Self::NotFound => (StatusCode::NOT_FOUND, self.to_string()),
            Self::InvalidRequest(_) => (StatusCode::BAD_REQUEST, self.to_string()),
            Self::AnalysisUnavailable(_) => (StatusCode::CONFLICT, self.to_string()),
            Self::Config(_)
            | Self::Database(_)
            | Self::Http(_)
            | Self::Provider(_)
            | Self::Internal(_) => {
                tracing::error!(error = %self, "request failed");
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "internal server error".to_owned(),
                )
            }
        };
        (status, Json(json!({ "error": message }))).into_response()
    }
}

impl From<std::io::Error> for AppError {
    fn from(value: std::io::Error) -> Self {
        Self::Internal(value.to_string())
    }
}

impl From<toml::de::Error> for AppError {
    fn from(value: toml::de::Error) -> Self {
        Self::Config(value.to_string())
    }
}
