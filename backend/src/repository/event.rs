use chrono::{DateTime, Duration, Utc};
use sqlx::{Row, SqlitePool};

use crate::{
    error::AppError,
    model::{EconomicEvent, EventObservation, EventStatus},
};

use super::{datetime_from_row, decimal_from_row, event_from_row};

#[derive(Clone)]
pub struct EventRepository {
    pool: SqlitePool,
}

impl EventRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn save_events(&self, events: &[EconomicEvent]) -> Result<Vec<i64>, AppError> {
        let mut transaction = self.pool.begin().await?;
        let mut ids = Vec::with_capacity(events.len());

        for event in events {
            let group_key = format!("{}|{}", event.country, event.event_time.timestamp());
            sqlx::query(
                r#"INSERT INTO release_group (group_key, country, release_time)
                   VALUES (?, ?, ?)
                   ON CONFLICT(group_key) DO UPDATE SET
                     country = excluded.country, release_time = excluded.release_time"#,
            )
            .bind(&group_key)
            .bind(&event.country)
            .bind(event.event_time.to_rfc3339())
            .execute(&mut *transaction)
            .await?;
            let release_group_id: i64 =
                sqlx::query_scalar("SELECT id FROM release_group WHERE group_key = ?")
                    .bind(&group_key)
                    .fetch_one(&mut *transaction)
                    .await?;

            sqlx::query(
                r#"INSERT INTO economic_event (
                    provider, provider_id, release_group_id, country, currency, category, event, event_time,
                    importance, actual, previous, consensus, forecast, unit, status, time_exact, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(provider, provider_id) DO UPDATE SET
                    release_group_id = excluded.release_group_id,
                    country = excluded.country,
                    currency = excluded.currency,
                    category = excluded.category,
                    event = excluded.event,
                    event_time = excluded.event_time,
                    importance = excluded.importance,
                    actual = excluded.actual,
                    previous = excluded.previous,
                    consensus = excluded.consensus,
                    forecast = excluded.forecast,
                    unit = excluded.unit,
                    time_exact = excluded.time_exact,
                    updated_at = excluded.updated_at
                WHERE economic_event.status != 'historical' OR excluded.status = 'historical'"#,
            )
            .bind(&event.provider)
            .bind(&event.provider_id)
            .bind(release_group_id)
            .bind(&event.country)
            .bind(&event.currency)
            .bind(&event.category)
            .bind(&event.event)
            .bind(event.event_time.to_rfc3339())
            .bind(i64::from(event.importance))
            .bind(event.actual.map(|value| value.to_string()))
            .bind(event.previous.map(|value| value.to_string()))
            .bind(event.consensus.map(|value| value.to_string()))
            .bind(event.forecast.map(|value| value.to_string()))
            .bind(&event.unit)
            .bind(event.status.as_str())
            .bind(event.time_exact)
            .bind(Utc::now().to_rfc3339())
            .execute(&mut *transaction)
            .await?;

            let id: i64 = sqlx::query_scalar(
                "SELECT id FROM economic_event WHERE provider = ? AND provider_id = ?",
            )
            .bind(&event.provider)
            .bind(&event.provider_id)
            .fetch_one(&mut *transaction)
            .await?;

            sqlx::query(
                r#"INSERT INTO event_observation
                    (event_id, observed_at, actual, previous, consensus, forecast)
                    VALUES (?, ?, ?, ?, ?, ?)"#,
            )
            .bind(id)
            .bind(Utc::now().to_rfc3339())
            .bind(event.actual.map(|value| value.to_string()))
            .bind(event.previous.map(|value| value.to_string()))
            .bind(event.consensus.map(|value| value.to_string()))
            .bind(event.forecast.map(|value| value.to_string()))
            .execute(&mut *transaction)
            .await?;

            ids.push(id);
        }

        transaction.commit().await?;
        Ok(ids)
    }

    pub async fn find_provider_event(
        &self,
        provider: &str,
        provider_id: &str,
    ) -> Result<Option<EconomicEvent>, AppError> {
        let row = sqlx::query("SELECT * FROM economic_event WHERE provider=? AND provider_id=?")
            .bind(provider)
            .bind(provider_id)
            .fetch_optional(&self.pool)
            .await?;
        row.map(event_from_row).transpose()
    }

    pub async fn get(&self, id: i64) -> Result<EconomicEvent, AppError> {
        let row = sqlx::query("SELECT * FROM economic_event WHERE id = ?")
            .bind(id)
            .fetch_optional(&self.pool)
            .await?
            .ok_or(AppError::NotFound)?;
        event_from_row(row)
    }

    pub async fn observations(&self, event_id: i64) -> Result<Vec<EventObservation>, AppError> {
        let rows = sqlx::query(
            "SELECT * FROM event_observation WHERE event_id = ? ORDER BY observed_at ASC",
        )
        .bind(event_id)
        .fetch_all(&self.pool)
        .await?;

        rows.into_iter()
            .map(|row| {
                Ok(EventObservation {
                    id: row.try_get("id")?,
                    event_id: row.try_get("event_id")?,
                    observed_at: datetime_from_row(&row, "observed_at")?,
                    actual: decimal_from_row(&row, "actual")?,
                    previous: decimal_from_row(&row, "previous")?,
                    consensus: decimal_from_row(&row, "consensus")?,
                    forecast: decimal_from_row(&row, "forecast")?,
                })
            })
            .collect()
    }

    pub async fn calendar(
        &self,
        from: DateTime<Utc>,
        to: DateTime<Utc>,
        country: Option<&str>,
        minimum_importance: Option<u8>,
    ) -> Result<Vec<EconomicEvent>, AppError> {
        let rows = sqlx::query(
            r#"SELECT * FROM economic_event
               WHERE event_time >= ? AND event_time <= ?
                 AND (? IS NULL OR lower(country) = lower(?))
                 AND importance >= ?
               ORDER BY event_time ASC, importance DESC"#,
        )
        .bind(from.to_rfc3339())
        .bind(to.to_rfc3339())
        .bind(country)
        .bind(country)
        .bind(i64::from(minimum_importance.unwrap_or(0)))
        .fetch_all(&self.pool)
        .await?;
        rows.into_iter().map(event_from_row).collect()
    }

    pub async fn upcoming(
        &self,
        now: DateTime<Utc>,
        days: i64,
    ) -> Result<Vec<EconomicEvent>, AppError> {
        self.calendar(now, now + Duration::days(days), None, None)
            .await
    }

    pub async fn history(
        &self,
        country: Option<&str>,
        category: Option<&str>,
        limit: u32,
    ) -> Result<Vec<EconomicEvent>, AppError> {
        self.history_page(country, category, limit, 0, None, None)
            .await
    }

    pub async fn history_page(
        &self,
        country: Option<&str>,
        category: Option<&str>,
        limit: u32,
        offset: u32,
        from: Option<DateTime<Utc>>,
        to: Option<DateTime<Utc>>,
    ) -> Result<Vec<EconomicEvent>, AppError> {
        let rows = sqlx::query(
            r#"SELECT * FROM economic_event
               WHERE event_time < ?
                 AND (? IS NULL OR lower(country) = lower(?))
                 AND (? IS NULL OR lower(category) LIKE '%' || lower(?) || '%')
                 AND (? IS NULL OR event_time >= ?)
                 AND (? IS NULL OR event_time < ?)
               ORDER BY event_time DESC, id DESC LIMIT ? OFFSET ?"#,
        )
        .bind(Utc::now().to_rfc3339())
        .bind(country)
        .bind(country)
        .bind(category)
        .bind(category)
        .bind(from.map(|v| v.to_rfc3339()))
        .bind(from.map(|v| v.to_rfc3339()))
        .bind(to.map(|v| v.to_rfc3339()))
        .bind(to.map(|v| v.to_rfc3339()))
        .bind(i64::from(limit.clamp(1, 500)))
        .bind(i64::from(offset))
        .fetch_all(&self.pool)
        .await?;
        rows.into_iter().map(event_from_row).collect()
    }

    pub async fn scheduled_to_watch(
        &self,
        now: DateTime<Utc>,
        watch_before_minutes: i64,
        release_timeout_minutes: i64,
        minimum_importance: u8,
    ) -> Result<Vec<EconomicEvent>, AppError> {
        let rows = sqlx::query(
            r#"SELECT * FROM economic_event
               WHERE status = 'scheduled' AND importance >= ?
                 AND event_time >= ? AND event_time <= ?
               ORDER BY event_time ASC"#,
        )
        .bind(i64::from(minimum_importance))
        .bind((now - Duration::minutes(release_timeout_minutes)).to_rfc3339())
        .bind((now + Duration::minutes(watch_before_minutes)).to_rfc3339())
        .fetch_all(&self.pool)
        .await?;
        rows.into_iter().map(event_from_row).collect()
    }

    pub async fn watching(&self) -> Result<Vec<EconomicEvent>, AppError> {
        self.by_statuses(&[EventStatus::Watching]).await
    }

    pub async fn market_active(&self) -> Result<Vec<EconomicEvent>, AppError> {
        self.by_statuses(&[
            EventStatus::Watching,
            EventStatus::Released,
            EventStatus::CollectingMarketData,
            EventStatus::Analyzing,
        ])
        .await
    }

    async fn by_statuses(&self, statuses: &[EventStatus]) -> Result<Vec<EconomicEvent>, AppError> {
        let names: Vec<&str> = statuses.iter().map(|status| status.as_str()).collect();
        let placeholders = vec!["?"; names.len()].join(",");
        let sql = format!(
            "SELECT * FROM economic_event WHERE status IN ({placeholders}) ORDER BY event_time ASC"
        );
        let mut query = sqlx::query(&sql);
        for name in names {
            query = query.bind(name);
        }
        let rows = query.fetch_all(&self.pool).await?;
        rows.into_iter().map(event_from_row).collect()
    }

    pub async fn set_status(&self, id: i64, status: EventStatus) -> Result<(), AppError> {
        let result =
            sqlx::query("UPDATE economic_event SET status = ?, updated_at = ? WHERE id = ?")
                .bind(status.as_str())
                .bind(Utc::now().to_rfc3339())
                .bind(id)
                .execute(&self.pool)
                .await?;
        if result.rows_affected() == 0 {
            return Err(AppError::NotFound);
        }
        Ok(())
    }
}
