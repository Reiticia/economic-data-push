use std::{env, fs, path::Path};

use serde::Deserialize;

use crate::error::AppError;

#[derive(Clone, Debug, Deserialize)]
pub struct AppConfig {
    pub server: ServerConfig,
    pub database: DatabaseConfig,
    pub calendar: CalendarConfig,
    pub scheduler: SchedulerConfig,
    pub market: MarketConfig,
    #[serde(default)]
    pub backfill: BackfillConfig,
}

#[derive(Clone, Debug, Deserialize)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
}

#[derive(Clone, Debug, Deserialize)]
pub struct DatabaseConfig {
    pub url: String,
}

#[derive(Clone, Debug, Deserialize)]
pub struct CalendarConfig {
    pub base_url: String,
    pub sync_days: i64,
    pub minimum_importance: u8,
}

#[derive(Clone, Debug, Deserialize)]
pub struct SchedulerConfig {
    pub calendar_sync_seconds: u64,
    pub watch_scan_seconds: u64,
    pub watch_before_minutes: i64,
    pub release_timeout_minutes: i64,
    pub market_poll_seconds: u64,
    pub market_collect_after_minutes: i64,
}

#[derive(Clone, Debug, Deserialize)]
pub struct MarketConfig {
    pub yahoo_base_url: String,
    pub binance_base_url: String,
    pub symbols: Vec<String>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(default)]
pub struct BackfillConfig {
    pub calendar_api_base_url: String,
    pub request_delay_ms: u64,
}

impl Default for BackfillConfig {
    fn default() -> Self {
        Self {
            calendar_api_base_url: "https://api.tradingeconomics.com".into(),
            request_delay_ms: 1000,
        }
    }
}

impl AppConfig {
    pub fn load() -> Result<Self, AppError> {
        let path = env::var("APP_CONFIG").unwrap_or_else(|_| "config.toml".to_owned());
        Self::from_path(path)
    }

    pub fn from_path(path: impl AsRef<Path>) -> Result<Self, AppError> {
        let contents = fs::read_to_string(path)?;
        let config = toml::from_str(&contents)?;
        Ok(config)
    }
}
