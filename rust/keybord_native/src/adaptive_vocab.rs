use rusqlite::{params, Connection, Result};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct WordEntry {
    pub word: String,
    pub frequency: f32,
    pub last_used: i64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PredictionCandidate {
    pub word: String,
    pub score: f32,
    pub source: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LearningEvent {
    NormalCommit = 0,
    SuggestionAccepted = 1,
    ManualCorrection = 2,
    ScrubDelete = 3,
}

impl From<i32> for LearningEvent {
    fn from(val: i32) -> Self {
        match val {
            1 => LearningEvent::SuggestionAccepted,
            2 => LearningEvent::ManualCorrection,
            3 => LearningEvent::ScrubDelete,
            _ => LearningEvent::NormalCommit,
        }
    }
}

pub struct UserHistoryStorage {
    conn: Connection,
}

impl UserHistoryStorage {
    pub fn new(conn: Connection) -> Result<Self> {
        let storage = Self { conn };
        storage.create_tables()?;
        Ok(storage)
    }

    pub fn new_in_memory() -> Result<Self> {
        let conn = Connection::open_in_memory()?;
        Self::new(conn)
    }

    pub fn open_file(path: &str) -> Result<Self> {
        let conn = Connection::open(path)?;
        Self::new(conn)
    }

    fn create_tables(&self) -> Result<()> {
        self.conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS unigrams (
                word TEXT PRIMARY KEY,
                count REAL NOT NULL DEFAULT 1.0,
                last_used INTEGER NOT NULL,
                is_promoted INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS bigrams (
                w1 TEXT NOT NULL,
                w2 TEXT NOT NULL,
                count REAL NOT NULL DEFAULT 1.0,
                last_used INTEGER NOT NULL,
                PRIMARY KEY (w1, w2)
            );
            CREATE TABLE IF NOT EXISTS trigrams (
                w1 TEXT NOT NULL,
                w2 TEXT NOT NULL,
                w3 TEXT NOT NULL,
                count REAL NOT NULL DEFAULT 1.0,
                last_used INTEGER NOT NULL,
                PRIMARY KEY (w1, w2, w3)
            );
            CREATE INDEX IF NOT EXISTS idx_unigrams_count ON unigrams(count DESC);
            CREATE INDEX IF NOT EXISTS idx_bigrams_w1 ON bigrams(w1, count DESC);
            CREATE INDEX IF NOT EXISTS idx_trigrams_w1_w2 ON trigrams(w1, w2, count DESC);",
        )?;
        Ok(())
    }

    pub fn record_unigram(&self, word: &str, weight: f32, now: i64) -> Result<bool> {
        let clean = word.trim().to_lowercase();
        if clean.is_empty() {
            return Ok(false);
        }

        self.conn.execute(
            "INSERT INTO unigrams (word, count, last_used, is_promoted)
             VALUES (?1, ?2, ?3, 0)
             ON CONFLICT(word) DO UPDATE SET
                count = count + ?2,
                last_used = ?3",
            params![clean, weight, now],
        )?;

        let count: f32 = self.conn.query_row(
            "SELECT count FROM unigrams WHERE word = ?1",
            params![clean],
            |r| r.get(0),
        )?;

        let promoted = if count >= 2.0 {
            self.conn.execute(
                "UPDATE unigrams SET is_promoted = 1 WHERE word = ?1",
                params![clean],
            )?;
            true
        } else {
            false
        };

        Ok(promoted)
    }

    pub fn penalize_word(&self, word: &str, penalty: f32) -> Result<()> {
        let clean = word.trim().to_lowercase();
        self.conn.execute(
            "UPDATE unigrams SET count = MAX(0.1, count - ?1) WHERE word = ?2",
            params![penalty, clean],
        )?;
        Ok(())
    }

    pub fn record_bigram(&self, w1: &str, w2: &str, weight: f32, now: i64) -> Result<()> {
        let c1 = w1.trim().to_lowercase();
        let c2 = w2.trim().to_lowercase();
        if c1.is_empty() || c2.is_empty() {
            return Ok(());
        }

        self.conn.execute(
            "INSERT INTO bigrams (w1, w2, count, last_used)
             VALUES (?1, ?2, ?3, ?4)
             ON CONFLICT(w1, w2) DO UPDATE SET
                count = count + ?3,
                last_used = ?4",
            params![c1, c2, weight, now],
        )?;
        Ok(())
    }

    pub fn record_trigram(&self, w1: &str, w2: &str, w3: &str, weight: f32, now: i64) -> Result<()> {
        let c1 = w1.trim().to_lowercase();
        let c2 = w2.trim().to_lowercase();
        let c3 = w3.trim().to_lowercase();
        if c1.is_empty() || c2.is_empty() || c3.is_empty() {
            return Ok(());
        }

        self.conn.execute(
            "INSERT INTO trigrams (w1, w2, w3, count, last_used)
             VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(w1, w2, w3) DO UPDATE SET
                count = count + ?4,
                last_used = ?5",
            params![c1, c2, c3, weight, now],
        )?;
        Ok(())
    }

    pub fn get_unigram_count(&self, word: &str) -> Result<f32> {
        let clean = word.trim().to_lowercase();
        let count: Result<f32> = self.conn.query_row(
            "SELECT count FROM unigrams WHERE word = ?1",
            params![clean],
            |r| r.get(0),
        );
        match count {
            Ok(c) => Ok(c),
            Err(rusqlite::Error::QueryReturnedNoRows) => Ok(0.0),
            Err(e) => Err(e),
        }
    }

    pub fn is_promoted(&self, word: &str) -> Result<bool> {
        let clean = word.trim().to_lowercase();
        let res: Result<i32> = self.conn.query_row(
            "SELECT is_promoted FROM unigrams WHERE word = ?1",
            params![clean],
            |r| r.get(0),
        );
        match res {
            Ok(p) => Ok(p == 1),
            Err(rusqlite::Error::QueryReturnedNoRows) => Ok(false),
            Err(e) => Err(e),
        }
    }

    pub fn search_prefix(&self, prefix: &str, limit: usize) -> Result<Vec<WordEntry>> {
        let clean = prefix.trim().to_lowercase();
        let pattern = format!("{}%", clean);
        let mut stmt = self.conn.prepare(
            "SELECT word, count, last_used FROM unigrams WHERE word LIKE ?1 ORDER BY count DESC LIMIT ?2",
        )?;
        let rows = stmt.query_map(params![pattern, limit as i64], |r| {
            Ok(WordEntry {
                word: r.get(0)?,
                frequency: r.get(1)?,
                last_used: r.get(2)?,
            })
        })?;

        let mut res = Vec::new();
        for r in rows {
            res.push(r?);
        }
        Ok(res)
    }

    pub fn predict_next_words(&self, context: &[String], limit: usize) -> Result<Vec<PredictionCandidate>> {
        let mut candidates = Vec::new();
        let mut seen = std::collections::HashSet::new();

        if context.len() >= 2 {
            let w1 = context[context.len() - 2].to_lowercase();
            let w2 = context[context.len() - 1].to_lowercase();
            let mut stmt = self.conn.prepare(
                "SELECT w3, count FROM trigrams WHERE w1 = ?1 AND w2 = ?2 ORDER BY count DESC LIMIT ?3",
            )?;
            let rows = stmt.query_map(params![w1, w2, limit as i64], |r| {
                Ok((r.get::<_, String>(0)?, r.get::<_, f32>(1)?))
            })?;
            for r in rows {
                let (w, cnt) = r?;
                if seen.insert(w.clone()) {
                    candidates.push(PredictionCandidate {
                        word: w,
                        score: cnt * 3.0,
                        source: "trigram".to_string(),
                    });
                }
            }
        }

        if candidates.len() < limit && !context.is_empty() {
            let w1 = context.last().unwrap().to_lowercase();
            let mut stmt = self.conn.prepare(
                "SELECT w2, count FROM bigrams WHERE w1 = ?1 ORDER BY count DESC LIMIT ?2",
            )?;
            let rows = stmt.query_map(params![w1, limit as i64], |r| {
                Ok((r.get::<_, String>(0)?, r.get::<_, f32>(1)?))
            })?;
            for r in rows {
                let (w, cnt) = r?;
                if seen.insert(w.clone()) {
                    candidates.push(PredictionCandidate {
                        word: w,
                        score: cnt * 1.5,
                        source: "bigram".to_string(),
                    });
                }
            }
        }

        if candidates.len() < limit {
            let remaining = limit - candidates.len();
            let mut stmt = self.conn.prepare(
                "SELECT word, count FROM unigrams WHERE is_promoted = 1 ORDER BY count DESC LIMIT ?1",
            )?;
            let rows = stmt.query_map(params![remaining as i64], |r| {
                Ok((r.get::<_, String>(0)?, r.get::<_, f32>(1)?))
            })?;
            for r in rows {
                let (w, cnt) = r?;
                if seen.insert(w.clone()) {
                    candidates.push(PredictionCandidate {
                        word: w,
                        score: cnt * 0.5,
                        source: "unigram".to_string(),
                    });
                }
            }
        }

        candidates.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));
        candidates.truncate(limit);
        Ok(candidates)
    }

    pub fn prune_decay(&self, cutoff_time_ms: i64) -> Result<usize> {
        let deleted = self.conn.execute(
            "DELETE FROM unigrams WHERE last_used < ?1 AND is_promoted = 0",
            params![cutoff_time_ms],
        )?;
        Ok(deleted)
    }
}

pub struct AdaptiveVocabularyEngine {
    pub storage: UserHistoryStorage,
}

impl AdaptiveVocabularyEngine {
    pub fn new(storage: UserHistoryStorage) -> Self {
        Self { storage }
    }

    pub fn on_word_committed(&self, word: &str, context: &[String], event: LearningEvent, now: i64) -> Result<()> {
        let (unigram_weight, bigram_weight, trigram_weight) = match event {
            LearningEvent::NormalCommit => (1.0f32, 1.0f32, 1.0f32),
            LearningEvent::SuggestionAccepted => (3.0, 2.5, 2.0),
            LearningEvent::ManualCorrection => (4.0, 3.0, 2.5),
            LearningEvent::ScrubDelete => {
                self.storage.penalize_word(word, 2.0)?;
                return Ok(());
            }
        };

        self.storage.record_unigram(word, unigram_weight, now)?;

        if !context.is_empty() {
            let prev = context.last().unwrap();
            self.storage.record_bigram(prev, word, bigram_weight, now)?;
        }

        if context.len() >= 2 {
            let w1 = &context[context.len() - 2];
            let w2 = &context[context.len() - 1];
            self.storage.record_trigram(w1, w2, word, trigram_weight, now)?;
        }

        Ok(())
    }
}

// ============================================================================
// TESTS — Strictly matching PRD Section 5.1 requirements
// ============================================================================
#[cfg(test)]
mod tests {
    use super::*;

    // 1. test_unigram_insert_and_retrieve()
    #[test]
    fn test_unigram_insert_and_retrieve() {
        let storage = UserHistoryStorage::new_in_memory().unwrap();
        storage.record_unigram("rustacean", 1.0, 1000).unwrap();
        let count = storage.get_unigram_count("rustacean").unwrap();
        assert!((count - 1.0).abs() < 1e-5);
    }

    // 2. test_bigram_completion_order()
    #[test]
    fn test_bigram_completion_order() {
        let storage = UserHistoryStorage::new_in_memory().unwrap();
        storage.record_bigram("machine", "learning", 5.0, 1000).unwrap();
        storage.record_bigram("machine", "vision", 2.0, 1000).unwrap();

        let predictions = storage.predict_next_words(&["machine".to_string()], 5).unwrap();
        assert!(predictions.len() >= 2);
        assert_eq!(predictions[0].word, "learning");
        assert_eq!(predictions[1].word, "vision");
    }

    // 3. test_oov_promotion_after_threshold()
    #[test]
    fn test_oov_promotion_after_threshold() {
        let storage = UserHistoryStorage::new_in_memory().unwrap();
        assert!(!storage.is_promoted("customword").unwrap());

        // First use: count = 1.0 < 2.0
        storage.record_unigram("customword", 1.0, 1000).unwrap();
        assert!(!storage.is_promoted("customword").unwrap());

        // Second use: count = 2.0 >= 2.0 -> Promoted
        storage.record_unigram("customword", 1.0, 2000).unwrap();
        assert!(storage.is_promoted("customword").unwrap());
    }

    // 4. test_decay_prune_removes_stale()
    #[test]
    fn test_decay_prune_removes_stale() {
        let storage = UserHistoryStorage::new_in_memory().unwrap();
        let now = 1000000i64;
        let stale_time = now - (31 * 24 * 3600 * 1000); // 31 days old

        storage.record_unigram("stale", 1.0, stale_time).unwrap();
        storage.record_unigram("fresh", 1.0, now).unwrap();

        let cutoff = now - (30 * 24 * 3600 * 1000);
        let pruned = storage.prune_decay(cutoff).unwrap();
        assert_eq!(pruned, 1);

        assert_eq!(storage.get_unigram_count("stale").unwrap(), 0.0);
        assert!((storage.get_unigram_count("fresh").unwrap() - 1.0).abs() < 1e-5);
    }

    // 5. test_rejection_penalty_lowers_score()
    #[test]
    fn test_rejection_penalty_lowers_score() {
        let storage = UserHistoryStorage::new_in_memory().unwrap();
        storage.record_unigram("typo", 3.0, 1000).unwrap();

        let before = storage.get_unigram_count("typo").unwrap();
        assert!((before - 3.0).abs() < 1e-5);

        storage.penalize_word("typo", 2.0).unwrap();
        let after = storage.get_unigram_count("typo").unwrap();
        assert!((after - 1.0).abs() < 1e-5);
    }
}
