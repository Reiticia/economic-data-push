use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};

use async_trait::async_trait;
use axum::{Json, Router, http::HeaderMap, routing::post};
use chrono::{Duration, Utc};
use market_event_analyzer::{
    error::AppError,
    model::{EconomicEvent, EventStatus},
    repository::EventRepository,
    translation::{
        EventNameTranslation, EventNameTranslator, OpenAiEventNameTranslator, TranslationService,
    },
};
use serde_json::Value;
use sqlx::sqlite::SqlitePoolOptions;

struct CountingTranslator {
    calls: AtomicUsize,
}

#[async_trait]
impl EventNameTranslator for CountingTranslator {
    async fn translate(&self, names: &[String]) -> Result<Vec<EventNameTranslation>, AppError> {
        self.calls.fetch_add(1, Ordering::SeqCst);
        Ok(names
            .iter()
            .map(|source| EventNameTranslation {
                source: source.clone(),
                zh_cn: "消费者价格指数同比".into(),
                zh_tw: "消費者價格指數同比".into(),
            })
            .collect())
    }
}

fn event(provider_id: &str) -> EconomicEvent {
    EconomicEvent {
        id: 0,
        provider: "fixture".into(),
        provider_id: provider_id.into(),
        release_group_id: None,
        country: "United States".into(),
        currency: Some("USD".into()),
        category: "inflation".into(),
        event: "CPI YoY".into(),
        event_zh_cn: None,
        event_zh_tw: None,
        event_time: Utc::now() + Duration::days(1),
        importance: 3,
        actual: None,
        previous: None,
        consensus: None,
        forecast: None,
        unit: Some("%".into()),
        status: EventStatus::Scheduled,
        time_exact: true,
    }
}

#[tokio::test]
async fn persisted_translation_is_reused_for_the_same_event_name() {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .unwrap();
    sqlx::migrate!("./migrations").run(&pool).await.unwrap();
    let repository = EventRepository::new(pool);
    let translator = Arc::new(CountingTranslator {
        calls: AtomicUsize::new(0),
    });
    let service = TranslationService::new(repository.clone(), translator.clone(), 20);

    let mut first = vec![event("first")];
    service.enrich(&mut first).await.unwrap();
    repository.save_events(&first).await.unwrap();

    let mut second = vec![event("second")];
    service.enrich(&mut second).await.unwrap();
    repository.save_events(&second).await.unwrap();

    assert_eq!(translator.calls.load(Ordering::SeqCst), 1);
    assert_eq!(second[0].event_zh_cn.as_deref(), Some("消费者价格指数同比"));
    assert_eq!(second[0].event_zh_tw.as_deref(), Some("消費者價格指數同比"));

    let third_id = repository
        .save_events(&[event("saved-without-enrichment")])
        .await
        .unwrap()[0];
    let stored = repository.get(third_id).await.unwrap();
    assert_eq!(stored.event_zh_cn.as_deref(), Some("消费者价格指数同比"));
    assert_eq!(translator.calls.load(Ordering::SeqCst), 1);
}

#[tokio::test]
async fn startup_backfill_updates_existing_untranslated_rows() {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:")
        .await
        .unwrap();
    sqlx::migrate!("./migrations").run(&pool).await.unwrap();
    let repository = EventRepository::new(pool);
    let id = repository.save_events(&[event("existing")]).await.unwrap()[0];
    let translator = Arc::new(CountingTranslator {
        calls: AtomicUsize::new(0),
    });
    let service = TranslationService::new(repository.clone(), translator.clone(), 20);

    assert_eq!(service.backfill_existing().await.unwrap(), 1);
    let stored = repository.get(id).await.unwrap();

    assert_eq!(translator.calls.load(Ordering::SeqCst), 1);
    assert_eq!(stored.event_zh_cn.as_deref(), Some("消费者价格指数同比"));
    assert_eq!(stored.event_zh_tw.as_deref(), Some("消費者價格指數同比"));
}

#[tokio::test]
async fn openai_compatible_client_sends_a_batch_and_parses_json() {
    async fn translate(headers: HeaderMap, Json(body): Json<Value>) -> Json<Value> {
        assert_eq!(headers["authorization"], "Bearer test-key");
        assert_eq!(body["model"], "test-model");
        assert_eq!(body["response_format"]["type"], "json_object");
        let input: Value =
            serde_json::from_str(body["messages"][1]["content"].as_str().unwrap()).unwrap();
        assert_eq!(input["events"][0]["name"], "CPI YoY");
        Json(serde_json::json!({
            "choices": [{
                "message": {
                    "content": "{\"translations\":[{\"id\":0,\"zhCn\":\"消费者价格指数同比\",\"zhTw\":\"消費者價格指數同比\"}]}"
                }
            }]
        }))
    }

    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());
    let task = tokio::spawn(async move {
        axum::serve(
            listener,
            Router::new().route("/chat/completions", post(translate)),
        )
        .await
        .unwrap();
    });
    let translator =
        OpenAiEventNameTranslator::new(reqwest::Client::new(), &url, "test-model", "test-key")
            .unwrap();

    let translations = translator.translate(&["CPI YoY".into()]).await.unwrap();

    assert_eq!(translations[0].zh_cn, "消费者价格指数同比");
    assert_eq!(translations[0].zh_tw, "消費者價格指數同比");
    task.abort();
}
