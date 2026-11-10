use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ChunkState {
    RawTranscript,
    CleanedRewritten,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum RamblerSessionState {
    Idle,
    Streaming,
    StateARewritten,
    StateBTranscript,
    MutatedOutside,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TextChunk {
    pub chunk_id: i64,
    pub raw_text: String,
    pub cleaned_text: String,
    pub start_offset: usize,
    pub end_offset: usize,
    pub current_state: ChunkState,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RamblerConfig {
    pub idle_timeout_ms: i64,
    pub min_chunk_length: usize,
    pub auto_punctuation_enabled: bool,
    pub disfluency_filter_enabled: bool,
    pub number_normalization_enabled: bool,
    pub preserve_rewritten_on_revert: bool,
}

impl Default for RamblerConfig {
    fn default() -> Self {
        Self {
            idle_timeout_ms: 3000,
            min_chunk_length: 12,
            auto_punctuation_enabled: true,
            disfluency_filter_enabled: true,
            number_normalization_enabled: true,
            preserve_rewritten_on_revert: true,
        }
    }
}

pub struct RamblerTextProcessor {
    config: RamblerConfig,
    number_map: HashMap<&'static str, &'static str>,
}

impl RamblerTextProcessor {
    pub fn new(config: Option<RamblerConfig>) -> Self {
        let mut map = HashMap::new();
        map.insert("zero", "0");
        map.insert("one", "1");
        map.insert("two", "2");
        map.insert("three", "3");
        map.insert("four", "4");
        map.insert("five", "5");
        map.insert("six", "6");
        map.insert("seven", "7");
        map.insert("eight", "8");
        map.insert("nine", "9");
        map.insert("ten", "10");
        map.insert("twenty", "20");
        map.insert("fifty", "50");
        map.insert("hundred", "100");

        Self {
            config: config.unwrap_or_default(),
            number_map: map,
        }
    }

    pub fn process_raw_stream(&self, raw_text: &str, is_final_pause: bool) -> String {
        if raw_text.trim().is_empty() {
            return raw_text.to_string();
        }

        let mut text = raw_text.trim().to_string();

        // 1. Remove spoken disfluencies & stuttered tokens
        if self.config.disfluency_filter_enabled {
            let disfluency_re = Regex::new(r"(?i)\b(um|uh|uhh|umm|ah|ahh|er|err|like,\s*you\s*know)\b[,\s]*").unwrap();
            text = disfluency_re.replace_all(&text, "").to_string();

            text = Self::remove_stutters(&text);

            let spaces = Regex::new(r"\s{2,}").unwrap();
            text = spaces.replace_all(&text, " ").to_string();
        }

        // 2. Normalize spoken numbers and symbols
        if self.config.number_normalization_enabled {
            text = self.normalize_numbers(&text);
        }

        // 3. True-casing (Capitalize 'I', sentence starts)
        text = self.apply_true_casing(&text);

        // 4. Punctuation injection
        if self.config.auto_punctuation_enabled && is_final_pause {
            text = self.apply_smart_punctuation(&text);
        }

        text.trim().to_string()
    }

    fn remove_stutters(text: &str) -> String {
        let words: Vec<&str> = text.split_whitespace().collect();
        if words.is_empty() {
            return String::new();
        }
        let mut result = Vec::new();
        let mut prev_clean = String::new();
        for &w in &words {
            let clean = w.trim_matches(|c: char| !c.is_alphanumeric()).to_lowercase();
            if clean.is_empty() || clean != prev_clean {
                result.push(w);
                prev_clean = clean;
            }
        }
        result.join(" ")
    }

    fn normalize_numbers(&self, input: &str) -> String {
        let dollars_re = Regex::new(r"(?i)\b(\w+)\s+dollars?\b").unwrap();
        let text = dollars_re.replace_all(input, |caps: &regex::Captures| {
            let word = caps[1].to_lowercase();
            let num = self.number_map.get(word.as_str()).copied().unwrap_or(&caps[1]);
            format!("${}", num)
        }).to_string();

        let percent_re = Regex::new(r"(?i)\b(\w+)\s+percent\b").unwrap();
        let text = percent_re.replace_all(&text, |caps: &regex::Captures| {
            let word = caps[1].to_lowercase();
            let num = self.number_map.get(word.as_str()).copied().unwrap_or(&caps[1]);
            format!("{}%", num)
        }).to_string();

        text
    }

    fn apply_true_casing(&self, input: &str) -> String {
        if input.is_empty() {
            return String::new();
        }

        let standalone_i = Regex::new(r"(?:^|\s)i(?:\s|['.,?!]|$)").unwrap();
        let text = standalone_i.replace_all(input, |caps: &regex::Captures| {
            let s = &caps[0];
            if s.starts_with(' ') {
                " I".to_string()
            } else {
                "I".to_string()
            }
        }).to_string();

        let mut res = String::with_capacity(text.len());
        let mut capitalize_next = true;

        for c in text.chars() {
            if capitalize_next && c.is_alphabetic() {
                res.push(c.to_ascii_uppercase());
                capitalize_next = false;
            } else {
                res.push(c);
                if c == '.' || c == '?' || c == '!' {
                    capitalize_next = true;
                }
            }
        }
        res
    }

    fn apply_smart_punctuation(&self, input: &str) -> String {
        let trimmed = input.trim_end();
        if trimmed.is_empty() {
            return String::new();
        }

        let last_char = trimmed.chars().last().unwrap();
        if last_char == '.' || last_char == '?' || last_char == '!' {
            return trimmed.to_string();
        }

        let first_word = trimmed.split_whitespace().next().unwrap_or("").to_lowercase();
        let question_starters = [
            "who", "what", "where", "when", "why", "how",
            "is", "are", "do", "does", "did", "can", "could", "would", "should", "will"
        ];

        if question_starters.contains(&first_word.as_str()) {
            format!("{}?", trimmed)
        } else {
            format!("{}.", trimmed)
        }
    }
}

pub struct RamblerUndoStateMachine {
    config: RamblerConfig,
    history: Vec<TextChunk>,
    pub session_state: RamblerSessionState,
    chunk_counter: i64,
}

impl RamblerUndoStateMachine {
    pub fn new(config: Option<RamblerConfig>) -> Self {
        Self {
            config: config.unwrap_or_default(),
            history: Vec::new(),
            session_state: RamblerSessionState::Idle,
            chunk_counter: 1000,
        }
    }

    pub fn start_session(&mut self) {
        self.session_state = RamblerSessionState::Streaming;
    }

    pub fn register_chunk(&mut self, raw_text: &str, cleaned_text: &str, start_offset: usize) -> TextChunk {
        self.chunk_counter += 1;
        let chunk = TextChunk {
            chunk_id: self.chunk_counter,
            raw_text: raw_text.to_string(),
            cleaned_text: cleaned_text.to_string(),
            start_offset,
            end_offset: start_offset + cleaned_text.len(),
            current_state: ChunkState::CleanedRewritten,
        };
        self.history.push(chunk.clone());
        self.session_state = RamblerSessionState::StateARewritten;
        chunk
    }

    pub fn revert_to_raw(&mut self) -> Option<(TextChunk, String)> {
        let chunk = self.history.last_mut()?;
        if chunk.current_state == ChunkState::RawTranscript {
            return None;
        }

        chunk.current_state = ChunkState::RawTranscript;
        self.session_state = RamblerSessionState::StateBTranscript;
        Some((chunk.clone(), chunk.raw_text.clone()))
    }

    pub fn restore_cleaned(&mut self) -> Option<(TextChunk, String)> {
        let chunk = self.history.last_mut()?;
        if !self.config.preserve_rewritten_on_revert || chunk.current_state == ChunkState::CleanedRewritten {
            return None;
        }

        chunk.current_state = ChunkState::CleanedRewritten;
        self.session_state = RamblerSessionState::StateARewritten;
        Some((chunk.clone(), chunk.cleaned_text.clone()))
    }

    pub fn handle_text_mutation(&mut self, cursor_pos: usize) {
        if let Some(chunk) = self.history.last() {
            if cursor_pos < chunk.start_offset || cursor_pos > chunk.end_offset + 2 {
                self.session_state = RamblerSessionState::MutatedOutside;
                self.history.clear();
            }
        }
    }
}

// ============================================================================
// TESTS — Strictly matching PRD Section 5.1 requirements
// ============================================================================
#[cfg(test)]
mod tests {
    use super::*;

    // 1. test_disfluency_removal_um_uh()
    #[test]
    fn test_disfluency_removal_um_uh() {
        let processor = RamblerTextProcessor::new(None);
        let result = processor.process_raw_stream("um uh let's go to the store", false);
        assert!(!result.contains("um"));
        assert!(!result.contains("uh"));
        assert!(result.contains("Let's go to the store"));
    }

    // 2. test_stutter_removal()
    #[test]
    fn test_stutter_removal() {
        let processor = RamblerTextProcessor::new(None);
        let result = processor.process_raw_stream("we we went to the the park", false);
        assert!(!result.contains("we we"));
        assert!(!result.contains("the the"));
        assert!(result.contains("We went to the park"));
    }

    // 3. test_number_normalization_dollars()
    #[test]
    fn test_number_normalization_dollars() {
        let processor = RamblerTextProcessor::new(None);
        let result = processor.process_raw_stream("it costs five dollars today", false);
        assert!(result.contains("$5"));
    }

    // 4. test_question_punctuation()
    #[test]
    fn test_question_punctuation() {
        let processor = RamblerTextProcessor::new(None);
        let question = processor.process_raw_stream("where are we going", true);
        assert!(question.ends_with('?'));

        let statement = processor.process_raw_stream("we are going home", true);
        assert!(statement.ends_with('.'));
    }

    // 5. test_state_machine_revert_cycle()
    #[test]
    fn test_state_machine_revert_cycle() {
        let mut sm = RamblerUndoStateMachine::new(None);
        sm.start_session();

        let raw = "um the meeting is at five dollars";
        let cleaned = "The meeting is at $5.";
        sm.register_chunk(raw, cleaned, 0);

        assert_eq!(sm.session_state, RamblerSessionState::StateARewritten);

        // Revert to raw
        let revert = sm.revert_to_raw();
        assert!(revert.is_some());
        let (_, raw_text) = revert.unwrap();
        assert_eq!(raw_text, raw);
        assert_eq!(sm.session_state, RamblerSessionState::StateBTranscript);

        // Restore to cleaned
        let restore = sm.restore_cleaned();
        assert!(restore.is_some());
        let (_, cleaned_text) = restore.unwrap();
        assert_eq!(cleaned_text, cleaned);
        assert_eq!(sm.session_state, RamblerSessionState::StateARewritten);
    }
}
