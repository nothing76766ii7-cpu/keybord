use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum WritingStyle {
    Formal = 0,
    Casual = 1,
    Concise = 2,
    Elaborate = 3,
    Emojify = 4,
    Poetic = 5,
    Professional = 6,
    Friendly = 7,
}

impl From<i32> for WritingStyle {
    fn from(v: i32) -> Self {
        match v {
            1 => WritingStyle::Casual,
            2 => WritingStyle::Concise,
            3 => WritingStyle::Elaborate,
            4 => WritingStyle::Emojify,
            5 => WritingStyle::Poetic,
            6 => WritingStyle::Professional,
            7 => WritingStyle::Friendly,
            _ => WritingStyle::Formal,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum CorrectionType {
    Spelling,
    Grammar,
    Punctuation,
    Capitalization,
    Redundancy,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ProofreadCorrection {
    pub original_span: String,
    pub replacement_span: String,
    pub start_index: usize,
    pub end_index: usize,
    pub correction_type: CorrectionType,
    pub explanation: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ProofreadResult {
    pub original_text: String,
    pub corrected_text: String,
    pub corrections: Vec<ProofreadCorrection>,
    pub has_changes: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RewriteDraft {
    pub text: String,
    pub style: WritingStyle,
    pub instruction_class: String,
    pub suggested_follow_ups: Vec<String>,
}

pub struct ProofreadEngine {
    spelling_errors: HashMap<&'static str, &'static str>,
}

impl ProofreadEngine {
    pub fn new() -> Self {
        let mut map = HashMap::new();
        map.insert("teh", "the");
        map.insert("recieve", "receive");
        map.insert("seperate", "separate");
        map.insert("definately", "definitely");
        map.insert("untill", "until");
        map.insert("occured", "occurred");
        map.insert("truely", "truly");
        map.insert("wierd", "weird");
        map.insert("acheive", "achieve");
        map.insert("alot", "a lot");
        map.insert("goverment", "government");
        map.insert("tommorrow", "tomorrow");
        map.insert("dont", "don't");
        map.insert("cant", "can't");
        map.insert("wont", "won't");
        map.insert("isnt", "isn't");
        map.insert("im", "I'm");
        map.insert("ive", "I've");
        map.insert("ill", "I'll");
        map.insert("id", "I'd");
        Self {
            spelling_errors: map,
        }
    }

    pub fn proofread(&self, text: &str) -> ProofreadResult {
        if text.trim().is_empty() {
            return ProofreadResult {
                original_text: text.to_string(),
                corrected_text: text.to_string(),
                corrections: Vec::new(),
                has_changes: false,
            };
        }

        let mut corrections = Vec::new();

        // 1. Check spelling errors
        let words_regex = Regex::new(r"\b[a-zA-Z']+\b").unwrap();
        for m in words_regex.find_iter(text) {
            let word = m.as_str();
            let lower = word.to_lowercase();
            if let Some(&replacement) = self.spelling_errors.get(lower.as_str()) {
                if word != replacement {
                    let final_rep = if word.chars().next().map(|c| c.is_uppercase()).unwrap_or(false) {
                        let mut chars = replacement.chars();
                        match chars.next() {
                            None => String::new(),
                            Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(),
                        }
                    } else {
                        replacement.to_string()
                    };

                    corrections.push(ProofreadCorrection {
                        original_span: word.to_string(),
                        replacement_span: final_rep,
                        start_index: m.start(),
                        end_index: m.end(),
                        correction_type: CorrectionType::Spelling,
                        explanation: format!("Corrected spelling of '{}'", word),
                    });
                }
            }
        }

        // 2. Grammar patterns
        let grammar_patterns = [
            (Regex::new(r"(?i)\b(could|should|would)\s+of\b").unwrap(), "$1 have"),
            (Regex::new(r"(?i)\bthey\s+is\b").unwrap(), "they are"),
            (Regex::new(r"(?i)\bwe\s+was\b").unwrap(), "we were"),
            (Regex::new(r"(?i)\byou\s+was\b").unwrap(), "you were"),
            (Regex::new(r"(?i)\bhe\s+don't\b").unwrap(), "he doesn't"),
            (Regex::new(r"(?i)\bshe\s+don't\b").unwrap(), "she doesn't"),
        ];

        for (re, rep) in &grammar_patterns {
            for m in re.find_iter(text) {
                let replaced = re.replace(m.as_str(), *rep).to_string();
                corrections.push(ProofreadCorrection {
                    original_span: m.as_str().to_string(),
                    replacement_span: replaced,
                    start_index: m.start(),
                    end_index: m.end(),
                    correction_type: CorrectionType::Grammar,
                    explanation: "Grammatical agreement fix".to_string(),
                });
            }
        }

        // 3. Capitalization & Punctuation
        let trimmed = text.trim_end();
        if !trimmed.is_empty() {
            if let Some(first_char) = trimmed.chars().next() {
                if first_char.is_lowercase() {
                    corrections.push(ProofreadCorrection {
                        original_span: first_char.to_string(),
                        replacement_span: first_char.to_uppercase().to_string(),
                        start_index: 0,
                        end_index: first_char.len_utf8(),
                        correction_type: CorrectionType::Capitalization,
                        explanation: "Capitalize first word of sentence".to_string(),
                    });
                }
            }

            let standalone_i = Regex::new(r"(?:^|\s)i(?:\s|['.,?!]|$)").unwrap();
            for m in standalone_i.find_iter(text) {
                let s = m.as_str();
                let offset = if s.starts_with(' ') { m.start() + 1 } else { m.start() };
                corrections.push(ProofreadCorrection {
                    original_span: "i".to_string(),
                    replacement_span: "I".to_string(),
                    start_index: offset,
                    end_index: offset + 1,
                    correction_type: CorrectionType::Capitalization,
                    explanation: "Capitalize pronoun 'I'".to_string(),
                });
            }

            let last_char = trimmed.chars().last().unwrap();
            if last_char != '.' && last_char != '?' && last_char != '!' {
                let first_word = trimmed.split_whitespace().next().unwrap_or("").to_lowercase();
                let question_starters = ["who", "what", "where", "when", "why", "how", "can", "could", "would", "is", "are", "do", "does"];
                let expected = if question_starters.contains(&first_word.as_str()) { "?" } else { "." };
                corrections.push(ProofreadCorrection {
                    original_span: String::new(),
                    replacement_span: expected.to_string(),
                    start_index: trimmed.len(),
                    end_index: trimmed.len(),
                    correction_type: CorrectionType::Punctuation,
                    explanation: "Add missing sentence terminator".to_string(),
                });
            }
        }

        // Sort descending to apply replacements from right to left
        let mut sorted = corrections.clone();
        sorted.sort_by(|a, b| b.start_index.cmp(&a.start_index));

        let mut result_text = text.to_string();
        for c in &sorted {
            if c.start_index <= c.end_index && c.end_index <= result_text.len() {
                result_text.replace_range(c.start_index..c.end_index, &c.replacement_span);
            }
        }

        corrections.sort_by_key(|c| c.start_index);
        let has_changes = !corrections.is_empty();

        ProofreadResult {
            original_text: text.to_string(),
            corrected_text: result_text,
            corrections,
            has_changes,
        }
    }
}

pub struct StyleRewriteEngine {
    emoji_dict: HashMap<&'static str, &'static str>,
}

impl StyleRewriteEngine {
    pub fn new() -> Self {
        let mut map = HashMap::new();
        map.insert("love", "❤️");
        map.insert("great", "🔥");
        map.insert("awesome", "✨");
        map.insert("cool", "😎");
        map.insert("happy", "😊");
        map.insert("congrats", "🎉");
        map.insert("celebrate", "🥳");
        map.insert("party", "🎈");
        map.insert("work", "💼");
        map.insert("job", "💻");
        map.insert("study", "📚");
        map.insert("school", "🏫");
        map.insert("yes", "👍");
        map.insert("ok", "👌");
        map.insert("agree", "✅");
        map.insert("good", "🙌");
        map.insert("food", "🍽️");
        map.insert("pizza", "🍕");
        map.insert("coffee", "☕");
        map.insert("beer", "🍻");
        map.insert("idea", "💡");
        map.insert("think", "🤔");
        map.insert("question", "❓");
        map.insert("fire", "🔥");
        map.insert("thanks", "🙏");
        map.insert("thank", "🙏");
        map.insert("hello", "👋");
        map.insert("hi", "👋");
        map.insert("fast", "⚡");
        map.insert("time", "⏰");
        map.insert("money", "💰");
        map.insert("car", "🚗");
        Self { emoji_dict: map }
    }

    pub fn rewrite(&self, text: &str, style: WritingStyle) -> RewriteDraft {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return RewriteDraft {
                text: text.to_string(),
                style,
                instruction_class: "MODIFICATION".to_string(),
                suggested_follow_ups: Vec::new(),
            };
        }

        let rewritten = match style {
            WritingStyle::Formal => self.formalize(trimmed),
            WritingStyle::Casual => self.casualize(trimmed),
            WritingStyle::Concise => self.make_concise(trimmed),
            WritingStyle::Elaborate => self.elaborate(trimmed),
            WritingStyle::Emojify => self.emojify(trimmed),
            WritingStyle::Poetic => self.make_poetic(trimmed),
            WritingStyle::Professional => self.make_professional(trimmed),
            WritingStyle::Friendly => self.make_friendly(trimmed),
        };

        let follow_ups = match style {
            WritingStyle::Formal => vec!["Add greeting".to_string(), "Make more concise".to_string(), "Include deadline".to_string()],
            WritingStyle::Casual => vec!["Add emoji".to_string(), "Make shorter".to_string(), "Sounds good".to_string()],
            WritingStyle::Concise => vec!["Make formal".to_string(), "Add gratitude".to_string(), "Ask for confirmation".to_string()],
            WritingStyle::Elaborate => vec!["Summarize".to_string(), "Make polite".to_string(), "Request review".to_string()],
            WritingStyle::Emojify => vec!["More emojis".to_string(), "Make casual".to_string(), "Tone down".to_string()],
            WritingStyle::Poetic => vec!["Make playful".to_string(), "Shorter verse".to_string(), "Rhyme more".to_string()],
            WritingStyle::Professional => vec!["Add closing".to_string(), "Make shorter".to_string(), "Request feedback".to_string()],
            WritingStyle::Friendly => vec!["Add thanks".to_string(), "Keep warm".to_string(), "Shorten".to_string()],
        };

        RewriteDraft {
            text: rewritten,
            style,
            instruction_class: "MODIFICATION".to_string(),
            suggested_follow_ups: follow_ups,
        }
    }

    fn formalize(&self, text: &str) -> String {
        let expansions = [
            (Regex::new(r"(?i)\bcan't\b").unwrap(), "cannot"),
            (Regex::new(r"(?i)\bdon't\b").unwrap(), "do not"),
            (Regex::new(r"(?i)\bwon't\b").unwrap(), "will not"),
            (Regex::new(r"(?i)\bwanna\b").unwrap(), "would like to"),
            (Regex::new(r"(?i)\bgonna\b").unwrap(), "going to"),
            (Regex::new(r"(?i)\bgimme\b").unwrap(), "please provide"),
            (Regex::new(r"(?i)\bthanks\b").unwrap(), "Thank you"),
            (Regex::new(r"(?i)\bhey\b").unwrap(), "Greetings,"),
            (Regex::new(r"(?i)\byeah|yep\b").unwrap(), "Yes, certainly"),
        ];

        let mut res = text.to_string();
        for (re, rep) in &expansions {
            res = re.replace_all(&res, *rep).to_string();
        }

        res = Regex::new(r"(?i)^send me\b").unwrap().replace(&res, "Could you please send me").to_string();
        res = Regex::new(r"(?i)^tell me\b").unwrap().replace(&res, "Please let me know").to_string();

        if !res.ends_with('.') && !res.ends_with('?') {
            res.push('.');
        }

        let mut chars = res.chars();
        match chars.next() {
            None => String::new(),
            Some(f) => f.to_uppercase().collect::<String>() + chars.as_str(),
        }
    }

    fn casualize(&self, text: &str) -> String {
        let mut res = text.to_string();
        res = Regex::new(r"(?i)\bdo not\b").unwrap().replace_all(&res, "don't").to_string();
        res = Regex::new(r"(?i)\bcannot\b").unwrap().replace_all(&res, "can't").to_string();
        res = Regex::new(r"(?i)\bwill not\b").unwrap().replace_all(&res, "won't").to_string();
        res = Regex::new(r"(?i)\bwould like to\b").unwrap().replace_all(&res, "wanna").to_string();
        res = Regex::new(r"(?i)^greetings,?").unwrap().replace_all(&res, "Hey!").to_string();

        if res.ends_with('.') {
            res.pop();
            res.push('!');
        }
        res
    }

    fn make_concise(&self, text: &str) -> String {
        let wordy_phrases = [
            (Regex::new(r"(?i)\bin order to\b").unwrap(), "to"),
            (Regex::new(r"(?i)\bdue to the fact that\b").unwrap(), "because"),
            (Regex::new(r"(?i)\bat this point in time\b").unwrap(), "currently"),
            (Regex::new(r"(?i)\bat the present moment\b").unwrap(), "now"),
            (Regex::new(r"(?i)\bfor the purpose of\b").unwrap(), "for"),
            (Regex::new(r"(?i)\bi am writing to let you know that\s*").unwrap(), ""),
            (Regex::new(r"(?i)\bplease be advised that\s*").unwrap(), ""),
        ];

        let mut res = text.to_string();
        for (re, rep) in &wordy_phrases {
            res = re.replace_all(&res, *rep).to_string();
        }

        let spaces = Regex::new(r"\s{2,}").unwrap();
        let cleaned = spaces.replace_all(res.trim(), " ").to_string();
        let mut chars = cleaned.chars();
        match chars.next() {
            None => String::new(),
            Some(f) => f.to_uppercase().collect::<String>() + chars.as_str(),
        }
    }

    fn elaborate(&self, text: &str) -> String {
        let base = text.trim_end_matches(['.', '!', '?']);
        format!(
            "I wanted to reach out regarding this: {}. Please let me know if you need any additional context or clarification, and I will be glad to follow up.",
            base
        )
    }

    fn emojify(&self, text: &str) -> String {
        let words: Vec<&str> = text.split_whitespace().collect();
        let mut parts = Vec::new();
        let clean_re = Regex::new(r"[^a-zA-Z]").unwrap();

        for w in words {
            parts.push(w.to_string());
            let clean = clean_re.replace_all(w, "").to_lowercase();
            if let Some(&emoji) = self.emoji_dict.get(clean.as_str()) {
                parts.push(emoji.to_string());
            }
        }

        parts.join(" ")
    }

    fn make_poetic(&self, text: &str) -> String {
        let base = text.trim_end_matches(['.', '!', '?']);
        format!("Beneath the sky of quiet thought, {}, as destiny softly sought.", base)
    }

    fn make_professional(&self, text: &str) -> String {
        let formal = self.formalize(text);
        if formal.to_lowercase().starts_with("hello") || formal.to_lowercase().starts_with("please") {
            formal
        } else {
            let mut chars = formal.chars();
            let lowered = match chars.next() {
                None => String::new(),
                Some(f) => f.to_lowercase().collect::<String>() + chars.as_str(),
            };
            format!("Please note: {}", lowered)
        }
    }

    fn make_friendly(&self, text: &str) -> String {
        let mut res = text.trim().to_string();
        if !res.to_lowercase().starts_with("hey") && !res.to_lowercase().starts_with("hi") {
            res = format!("Hey! {}", res);
        }
        if res.ends_with('.') {
            res.pop();
            res.push('!');
        } else if !res.ends_with('!') && !res.ends_with('?') {
            res.push('!');
        }
        format!("{} 😊", res)
    }
}

// ============================================================================
// TESTS — Strictly matching PRD Section 5.1 requirements
// ============================================================================
#[cfg(test)]
mod tests {
    use super::*;

    // 1. test_spelling_correction_teh()
    #[test]
    fn test_spelling_correction_teh() {
        let engine = ProofreadEngine::new();
        let result = engine.proofread("teh quick cat");
        assert!(result.has_changes);
        assert!(result.corrected_text.starts_with("The") || result.corrected_text.contains("the"));
    }

    // 2. test_grammar_could_of()
    #[test]
    fn test_grammar_could_of() {
        let engine = ProofreadEngine::new();
        let result = engine.proofread("I could of gone home.");
        assert!(result.has_changes);
        assert!(result.corrected_text.contains("could have"));
    }

    // 3. test_capitalize_i()
    #[test]
    fn test_capitalize_i() {
        let engine = ProofreadEngine::new();
        let result = engine.proofread("yesterday i went to work");
        assert!(result.has_changes);
        assert!(result.corrected_text.contains(" I "));
    }

    // 4. test_formal_expands_contractions()
    #[test]
    fn test_formal_expands_contractions() {
        let engine = StyleRewriteEngine::new();
        let draft = engine.rewrite("I don't wanna go", WritingStyle::Formal);
        assert!(draft.text.contains("do not"));
        assert!(draft.text.contains("would like to"));
    }

    // 5. test_concise_removes_wordy_phrases()
    #[test]
    fn test_concise_removes_wordy_phrases() {
        let engine = StyleRewriteEngine::new();
        let draft = engine.rewrite("in order to succeed, study hard", WritingStyle::Concise);
        assert!(!draft.text.to_lowercase().contains("in order to"));
        assert!(draft.text.to_lowercase().starts_with("to succeed"));
    }

    // 6. test_emojify_injects_emoji()
    #[test]
    fn test_emojify_injects_emoji() {
        let engine = StyleRewriteEngine::new();
        let draft = engine.rewrite("I love coffee", WritingStyle::Emojify);
        assert!(draft.text.contains("❤️"));
        assert!(draft.text.contains("☕"));
    }
}
