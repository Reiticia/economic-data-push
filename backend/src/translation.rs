use std::{collections::HashMap, sync::Arc};

use async_trait::async_trait;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use tokio::sync::Mutex;

use crate::{error::AppError, model::EconomicEvent, repository::EventRepository};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EventNameTranslation {
    pub source: String,
    pub zh_cn: String,
    pub zh_tw: String,
}

#[async_trait]
pub trait EventNameTranslator: Send + Sync {
    async fn translate(
        &self,
        event_names: &[String],
    ) -> Result<Vec<EventNameTranslation>, AppError>;
}

pub struct OpenAiEventNameTranslator {
    client: Client,
    endpoint: String,
    model: String,
    api_key: String,
}

impl OpenAiEventNameTranslator {
    pub fn new(
        client: Client,
        base_url: &str,
        model: impl Into<String>,
        api_key: impl Into<String>,
    ) -> Result<Self, AppError> {
        let base_url = base_url.trim().trim_end_matches('/');
        if base_url.is_empty() {
            return Err(AppError::Config(
                "translation.base_url must not be empty".into(),
            ));
        }
        let model = model.into();
        if model.trim().is_empty() {
            return Err(AppError::Config(
                "translation.model must not be empty".into(),
            ));
        }
        let api_key = api_key.into();
        if api_key.trim().is_empty() {
            return Err(AppError::Config(
                "translation API key must not be empty".into(),
            ));
        }
        Ok(Self {
            client,
            endpoint: format!("{base_url}/chat/completions"),
            model,
            api_key,
        })
    }
}

#[derive(Serialize)]
struct TranslationInput<'a> {
    events: Vec<TranslationInputRow<'a>>,
}

#[derive(Serialize)]
struct TranslationInputRow<'a> {
    id: usize,
    name: &'a str,
}

#[derive(Deserialize)]
struct TranslationOutput {
    translations: Vec<TranslationOutputRow>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TranslationOutputRow {
    id: usize,
    zh_cn: String,
    zh_tw: String,
}

#[derive(Deserialize)]
struct ChatResponse {
    choices: Vec<ChatChoice>,
}

#[derive(Deserialize)]
struct ChatChoice {
    message: ChatMessage,
}

#[derive(Deserialize)]
struct ChatMessage {
    content: String,
}

#[async_trait]
impl EventNameTranslator for OpenAiEventNameTranslator {
    async fn translate(
        &self,
        event_names: &[String],
    ) -> Result<Vec<EventNameTranslation>, AppError> {
        if event_names.is_empty() {
            return Ok(Vec::new());
        }
        let input = TranslationInput {
            events: event_names
                .iter()
                .enumerate()
                .map(|(id, name)| TranslationInputRow { id, name })
                .collect(),
        };
        let payload = serde_json::json!({
            "model": self.model,
            "messages": [
                {
                    "role": "system",
                    "content": "You translate macroeconomic calendar event names. Return concise, standard financial terminology in Simplified Chinese and Traditional Chinese. Preserve numbers, periods, abbreviations, and meanings exactly. Return JSON only, with every input id exactly once, in this schema: {\"translations\":[{\"id\":0,\"zhCn\":\"...\",\"zhTw\":\"...\"}]}"
                },
                {
                    "role": "user",
                    "content": serde_json::to_string(&input).map_err(|error| AppError::Internal(error.to_string()))?
                }
            ],
            "response_format": {"type": "json_object"}
        });
        let response: ChatResponse = self
            .client
            .post(&self.endpoint)
            .bearer_auth(&self.api_key)
            .json(&payload)
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;
        let content = response
            .choices
            .first()
            .ok_or_else(|| AppError::Provider("translation model returned no choices".into()))?
            .message
            .content
            .trim();
        let content = content
            .strip_prefix("```json")
            .or_else(|| content.strip_prefix("```"))
            .unwrap_or(content)
            .strip_suffix("```")
            .unwrap_or(content)
            .trim();
        let output: TranslationOutput = serde_json::from_str(content).map_err(|error| {
            AppError::Provider(format!("translation model returned invalid JSON: {error}"))
        })?;
        if output.translations.len() != event_names.len() {
            return Err(AppError::Provider(
                "translation model returned an incomplete result".into(),
            ));
        }
        let mut by_id = HashMap::with_capacity(output.translations.len());
        for row in output.translations {
            if row.id >= event_names.len()
                || row.zh_cn.trim().is_empty()
                || row.zh_tw.trim().is_empty()
                || by_id.insert(row.id, row).is_some()
            {
                return Err(AppError::Provider(
                    "translation model returned invalid translation rows".into(),
                ));
            }
        }
        event_names
            .iter()
            .enumerate()
            .map(|(id, source)| {
                let row = by_id.remove(&id).ok_or_else(|| {
                    AppError::Provider("translation model omitted an input id".into())
                })?;
                Ok(EventNameTranslation {
                    source: source.clone(),
                    zh_cn: row.zh_cn.trim().to_owned(),
                    zh_tw: row.zh_tw.trim().to_owned(),
                })
            })
            .collect()
    }
}

#[derive(Clone)]
pub struct TranslationService {
    events: EventRepository,
    translator: Arc<dyn EventNameTranslator>,
    batch_size: usize,
    lock: Arc<Mutex<()>>,
}

impl TranslationService {
    pub fn new(
        events: EventRepository,
        translator: Arc<dyn EventNameTranslator>,
        batch_size: usize,
    ) -> Self {
        Self {
            events,
            translator,
            batch_size: batch_size.clamp(1, 100),
            lock: Arc::new(Mutex::new(())),
        }
    }

    pub async fn enrich(&self, events: &mut [EconomicEvent]) -> Result<(), AppError> {
        let names = unique_names(
            events
                .iter()
                .filter(|event| event.event_zh_cn.is_none() || event.event_zh_tw.is_none()),
        );
        let translations = self.translate_missing(&names).await?;
        for event in events {
            if let Some((zh_cn, zh_tw)) = translations.get(&event.event) {
                event.event_zh_cn = Some(zh_cn.clone());
                event.event_zh_tw = Some(zh_tw.clone());
            }
        }
        Ok(())
    }

    pub async fn backfill_existing(&self) -> Result<usize, AppError> {
        let names = self.events.untranslated_event_names().await?;
        let count = names.len();
        self.translate_missing(&names).await?;
        Ok(count)
    }

    async fn translate_missing(
        &self,
        names: &[String],
    ) -> Result<HashMap<String, (String, String)>, AppError> {
        if names.is_empty() {
            return Ok(HashMap::new());
        }
        let _guard = self.lock.lock().await;
        let mut cached = HashMap::new();
        for batch in names.chunks(500) {
            cached.extend(self.events.cached_event_name_translations(batch).await?);
        }
        if !cached.is_empty() {
            let rows: Vec<_> = cached
                .iter()
                .map(|(source, (zh_cn, zh_tw))| (source.clone(), zh_cn.clone(), zh_tw.clone()))
                .collect();
            self.events.save_event_name_translations(&rows).await?;
        }
        let missing: Vec<String> = names
            .iter()
            .filter(|name| !cached.contains_key(*name))
            .cloned()
            .collect();
        for batch in missing.chunks(self.batch_size) {
            let translated = self.translator.translate(batch).await?;
            let rows: Vec<_> = translated
                .iter()
                .map(|row| (row.source.clone(), row.zh_cn.clone(), row.zh_tw.clone()))
                .collect();
            self.events.save_event_name_translations(&rows).await?;
            for row in translated {
                cached.insert(row.source, (row.zh_cn, row.zh_tw));
            }
        }
        Ok(cached)
    }
}

fn unique_names<'a>(events: impl Iterator<Item = &'a EconomicEvent>) -> Vec<String> {
    let mut names = Vec::new();
    for event in events {
        if !names.contains(&event.event) {
            names.push(event.event.clone());
        }
    }
    names
}
