use std::{
    net::{IpAddr, SocketAddr},
    path::Path,
    str::FromStr,
    sync::Arc,
    time::Duration,
};

use market_event_analyzer::{
    AppState,
    analysis::{AnalysisService, RuleEngine},
    api,
    backfill::{BackfillRange, BackfillService, repository::BackfillRepository},
    calendar::{CalendarService, TradingEconomicsApiProvider, TradingEconomicsProvider},
    config::AppConfig,
    market::{BinanceProvider, BiquoteProvider, MarketService, YahooProvider},
    model::MarketSymbol,
    repository::{AnalysisRepository, EventRepository, MarketRepository},
    scheduler,
    translation::{OpenAiEventNameTranslator, TranslationService},
};
use sqlx::{
    SqlitePool,
    sqlite::{SqliteConnectOptions, SqlitePoolOptions},
};
use tokio::sync::broadcast;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| EnvFilter::new("market_event_analyzer=info,tower_http=info")),
        )
        .init();

    let config = AppConfig::load()?;
    let args: Vec<String> = std::env::args().skip(1).collect();
    let history_range = if args.is_empty() {
        None
    } else {
        Some(BackfillRange::from_args(&args, chrono::Utc::now())?)
    };
    let history_key = if history_range.is_some() {
        Some(std::env::var("TE_API_KEY").ok().filter(|v| !v.trim().is_empty())
            .ok_or("Historical import requires TE_API_KEY with calendar history access. The public calendar page is not a historical data source.")?)
    } else {
        None
    };
    ensure_database_directory(&config.database.url)?;
    let options = SqliteConnectOptions::from_str(&config.database.url)?
        .create_if_missing(true)
        .foreign_keys(true);
    let pool: SqlitePool = SqlitePoolOptions::new()
        .max_connections(8)
        .connect_with(options)
        .await?;
    sqlx::migrate!("./migrations").run(&pool).await?;

    let http = reqwest::Client::builder()
        .user_agent("market-event-analyzer/0.1 (personal research tool)")
        .timeout(Duration::from_secs(20))
        .build()?;
    let events = EventRepository::new(pool.clone());
    let market = MarketRepository::new(pool.clone());
    let backfill = BackfillRepository::new(pool.clone());
    let analyses = AnalysisRepository::new(pool);
    let calendar_provider = Arc::new(TradingEconomicsProvider::new(
        http.clone(),
        &config.calendar.base_url,
    ));
    let translation_service = if config.translation.enabled {
        let api_key = std::env::var(&config.translation.api_key_env)
            .ok()
            .filter(|value| !value.trim().is_empty())
            .ok_or_else(|| {
                format!(
                    "translation is enabled but {} is missing",
                    config.translation.api_key_env
                )
            })?;
        let translator = Arc::new(OpenAiEventNameTranslator::new(
            http.clone(),
            &config.translation.base_url,
            config.translation.model.clone(),
            api_key,
        )?);
        Some(Arc::new(TranslationService::new(
            events.clone(),
            translator,
            config.translation.batch_size,
        )))
    } else {
        None
    };
    let mut calendar_service = CalendarService::new(calendar_provider, events.clone());
    if let Some(translation) = &translation_service {
        calendar_service = calendar_service.with_translation(translation.clone());
    }
    let calendar_service = Arc::new(calendar_service);
    let yahoo = Arc::new(YahooProvider::new(
        http.clone(),
        &config.market.yahoo_base_url,
    ));
    let binance = Arc::new(BinanceProvider::new(
        http.clone(),
        &config.market.binance_base_url,
    ));
    let biquote = Arc::new(BiquoteProvider::new(
        http.clone(),
        &config.market.biquote_base_url,
    ));
    let symbols = config
        .market
        .symbols
        .iter()
        .map(|symbol| MarketSymbol::from_str(symbol))
        .collect::<Result<Vec<_>, _>>()
        .map_err(|error| format!("invalid market symbol configuration: {error}"))?;
    let market_service = Arc::new(
        MarketService::new(yahoo, binance, market.clone(), symbols)
            .with_biquote(biquote)
            .with_live_quote_cache(
                Duration::from_secs(config.market.live_quote_cache_seconds),
                Duration::from_secs(config.market.live_quote_stale_seconds),
            ),
    );
    let rules = RuleEngine::from_path("rules.toml")?;
    let analysis_service = Arc::new(AnalysisService::new(
        events.clone(),
        market.clone(),
        analyses.clone(),
        rules,
    ));
    if let Some(range) = history_range {
        let provider = Arc::new(TradingEconomicsApiProvider::new(
            http,
            &config.backfill.calendar_api_base_url,
            history_key.unwrap(),
        )?);
        let mut service = BackfillService::new(
            backfill,
            provider,
            events,
            analyses,
            market_service,
            analysis_service,
            Duration::from_millis(config.backfill.request_delay_ms.max(250)),
        );
        if let Some(translation) = translation_service {
            service = service.with_translation(translation);
        }
        let summary = service.run(range).await?;
        println!("{}", serde_json::to_string_pretty(&summary)?);
        if summary.status != "complete" {
            return Err("Historical import is partial; inspect /api/v1/history/backfill and analysis historical.coverage before using the data".into());
        }
        return Ok(());
    }
    let (event_bus, _) = broadcast::channel(256);
    let state = AppState {
        events,
        market,
        analyses,
        calendar_service: calendar_service.clone(),
        market_service,
        analysis_service,
        event_bus,
        backfill,
    };

    if config.translation.backfill_on_startup
        && let Some(translation) = translation_service
    {
        tokio::spawn(async move {
            match translation.backfill_existing().await {
                Ok(0) => {}
                Ok(count) => tracing::info!(count, "existing event names translated"),
                Err(error) => tracing::warn!(%error, "existing event-name translation failed"),
            }
        });
    }

    tokio::spawn(scheduler::calendar_sync_loop(
        calendar_service,
        config.calendar.clone(),
        config.scheduler.calendar_sync_seconds,
    ));
    tokio::spawn(scheduler::event_watch_loop(
        state.clone(),
        config.calendar.clone(),
        config.scheduler.clone(),
    ));
    tokio::spawn(scheduler::market_collect_loop(
        state.clone(),
        config.scheduler.clone(),
    ));

    let address = SocketAddr::new(IpAddr::from_str(&config.server.host)?, config.server.port);
    let listener = tokio::net::TcpListener::bind(address).await?;
    tracing::info!(%address, "market event analyzer listening");
    axum::serve(listener, api::router(state))
        .with_graceful_shutdown(shutdown_signal())
        .await?;
    Ok(())
}

fn ensure_database_directory(url: &str) -> std::io::Result<()> {
    let path = url
        .strip_prefix("sqlite://")
        .or_else(|| url.strip_prefix("sqlite:"))
        .unwrap_or(url);
    let path = path.split('?').next().unwrap_or(path);
    if path == ":memory:" {
        return Ok(());
    }
    if let Some(parent) = Path::new(path)
        .parent()
        .filter(|parent| !parent.as_os_str().is_empty())
    {
        std::fs::create_dir_all(parent)?;
    }
    Ok(())
}

async fn shutdown_signal() {
    if let Err(error) = tokio::signal::ctrl_c().await {
        tracing::error!(%error, "failed to install shutdown signal handler");
    }
}
