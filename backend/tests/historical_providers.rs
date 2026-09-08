use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

use axum::{
    Json, Router,
    extract::{Query, State},
    http::StatusCode,
    routing::get,
};
use chrono::{Duration, TimeZone, Utc};
use market_event_analyzer::{
    calendar::{CalendarProvider, TradingEconomicsApiProvider},
    market::{BinanceProvider, MarketDataProvider},
    model::{Interval, MarketSymbol},
};
use serde::Deserialize;

async fn serve(router: Router) -> (String, tokio::task::JoinHandle<()>) {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());
    let handle = tokio::spawn(async move {
        axum::serve(listener, router).await.unwrap();
    });
    (url, handle)
}

#[tokio::test]
async fn calendar_errors_never_disclose_credentials() {
    let router = Router::new().route(
        "/calendar/country/All/{from}/{to}",
        get(|| async { StatusCode::FORBIDDEN }),
    );
    let (url, task) = serve(router).await;
    let provider =
        TradingEconomicsApiProvider::new(reqwest::Client::new(), &url, "secret-api-key".into())
            .unwrap();
    let start = Utc.with_ymd_and_hms(2026, 6, 8, 0, 0, 0).unwrap();
    let error = provider
        .fetch_events(start, start + Duration::days(1))
        .await
        .unwrap_err();
    assert!(!format!("{error:?}").contains("secret-api-key"));
    assert!(!error.to_string().contains("secret-api-key"));
    task.abort();
}

#[tokio::test]
async fn historical_api_rejects_out_of_range_response() {
    let router = Router::new().route("/calendar/country/All/{from}/{to}", get(|| async { Json(serde_json::json!([
        {"CalendarId": 1,"Date":"2026-09-08T12:30:00Z","Country":"United States","Category":"Inflation","Event":"CPI","DateSpan":0}
    ])) }));
    let (url, task) = serve(router).await;
    let provider =
        TradingEconomicsApiProvider::new(reqwest::Client::new(), &url, "test-key".into()).unwrap();
    let start = Utc.with_ymd_and_hms(2026, 6, 8, 0, 0, 0).unwrap();
    assert!(
        provider
            .fetch_events(start, start + Duration::days(1))
            .await
            .is_err()
    );
    task.abort();
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct KlineQuery {
    start_time: i64,
    end_time: i64,
}

#[tokio::test]
async fn binance_paginates_beyond_one_thousand_candles() {
    let count = Arc::new(AtomicUsize::new(0));
    let start = Utc.with_ymd_and_hms(2026, 6, 8, 0, 0, 0).unwrap();
    let epoch = start.timestamp_millis();
    let router = Router::new().route("/klines", get(move |State(calls): State<Arc<AtomicUsize>>, Query(q): Query<KlineQuery>| async move {
        calls.fetch_add(1, Ordering::SeqCst);
        let first = ((q.start_time - epoch) + 59999) / 60000;
        let rows: Vec<_> = (first..1002).take(1000).filter(|i| epoch + i * 60000 <= q.end_time)
            .map(|i| serde_json::json!([epoch+i*60000,"100","101","99","100","1"])) .collect();
        Json(rows)
    })).with_state(count.clone());
    let (url, task) = serve(router).await;
    let provider = BinanceProvider::new(reqwest::Client::new(), url);
    let candles = provider
        .candles(
            MarketSymbol::Bitcoin,
            start,
            start + Duration::minutes(1002),
            Interval::OneMinute,
        )
        .await
        .unwrap();
    assert_eq!(candles.len(), 1002);
    assert_eq!(count.load(Ordering::SeqCst), 2);
    assert_eq!(
        candles.last().unwrap().timestamp,
        start + Duration::minutes(1001)
    );
    task.abort();
}
