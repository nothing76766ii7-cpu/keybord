pub mod adaptive_vocab;
pub mod glide_engine;
pub mod magic_writing;
pub mod rambler;

use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::collections::HashMap;

// ----------------------------------------------------------------------------
// GLIDE JNI EXPORTS
// ----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_keyboard_engine_bridge_NativeBridge_glideDecodeGesture(
    mut env: JNIEnv,
    _class: JClass,
    points_json: JString,
    key_positions_json: JString,
    lexicon_words: JObjectArray,
    last_tap_timestamp: jlong,
) -> jstring {
    let points_str: String = match env.get_string(&points_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };
    let keys_str: String = match env.get_string(&key_positions_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let points: Vec<glide_engine::TouchPoint> = serde_json::from_str(&points_str).unwrap_or_default();
    let positions_map: HashMap<char, glide_engine::KeyPosition> = serde_json::from_str(&keys_str).unwrap_or_default();

    let mut lexicon = glide_engine::TrieLexicon::new();
    lexicon.load_default_vocabulary();

    let words_len = env.get_array_length(&lexicon_words).unwrap_or(0);
    for i in 0..words_len {
        if let Ok(obj) = env.get_object_array_element(&lexicon_words, i) {
            let jstr: JString = obj.into();
            let s_opt: Option<String> = env.get_string(&jstr).ok().map(|s| s.into());
            if let Some(s) = s_opt {
                lexicon.insert(&s, 50.0);
            }
        }
    }

    let mut decoder = glide_engine::GlideDecoder::new(positions_map, lexicon, None);
    decoder.notify_tap(last_tap_timestamp);

    let candidates = decoder.decode_gesture(&points);
    let json_out = serde_json::to_string(&candidates).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json_out).unwrap().into_raw()
}

// ----------------------------------------------------------------------------
// VOCABULARY JNI EXPORTS
// ----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_keyboard_engine_bridge_NativeBridge_vocabOnWordCommitted(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
    word: JString,
    context_json: JString,
    event_ordinal: jint,
) {
    let db_path_str: String = match env.get_string(&db_path) {
        Ok(s) => s.into(),
        Err(_) => return,
    };
    let word_str: String = match env.get_string(&word) {
        Ok(s) => s.into(),
        Err(_) => return,
    };
    let ctx_str: String = match env.get_string(&context_json) {
        Ok(s) => s.into(),
        Err(_) => return,
    };

    let context: Vec<String> = serde_json::from_str(&ctx_str).unwrap_or_default();
    let storage = match adaptive_vocab::UserHistoryStorage::open_file(&db_path_str) {
        Ok(s) => s,
        Err(_) => return,
    };

    let engine = adaptive_vocab::AdaptiveVocabularyEngine::new(storage);
    let event = adaptive_vocab::LearningEvent::from(event_ordinal);
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;

    let _ = engine.on_word_committed(&word_str, &context, event, now);
}

#[no_mangle]
pub extern "system" fn Java_com_keyboard_engine_bridge_NativeBridge_vocabPredictNext(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
    context_json: JString,
    max_candidates: jint,
) -> jstring {
    let db_path_str: String = match env.get_string(&db_path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };
    let ctx_str: String = match env.get_string(&context_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let context: Vec<String> = serde_json::from_str(&ctx_str).unwrap_or_default();
    let storage = match adaptive_vocab::UserHistoryStorage::open_file(&db_path_str) {
        Ok(s) => s,
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let predictions = storage.predict_next_words(&context, max_candidates as usize).unwrap_or_default();
    let json_out = serde_json::to_string(&predictions).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json_out).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_keyboard_engine_bridge_NativeBridge_vocabSearchPrefix(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
    prefix: JString,
    limit: jint,
) -> jstring {
    let db_path_str: String = match env.get_string(&db_path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };
    let prefix_str: String = match env.get_string(&prefix) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let storage = match adaptive_vocab::UserHistoryStorage::open_file(&db_path_str) {
        Ok(s) => s,
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };

    let entries = storage.search_prefix(&prefix_str, limit as usize).unwrap_or_default();
    let json_out = serde_json::to_string(&entries).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json_out).unwrap().into_raw()
}

// ----------------------------------------------------------------------------
// MAGIC WRITING JNI EXPORTS
// ----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_keyboard_engine_bridge_NativeBridge_magicProofread(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    let text_str: String = match env.get_string(&text) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let engine = magic_writing::ProofreadEngine::new();
    let result = engine.proofread(&text_str);
    let json_out = serde_json::to_string(&result).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json_out).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_keyboard_engine_bridge_NativeBridge_magicRewrite(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
    style_ordinal: jint,
) -> jstring {
    let text_str: String = match env.get_string(&text) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };

    let engine = magic_writing::StyleRewriteEngine::new();
    let style = magic_writing::WritingStyle::from(style_ordinal);
    let draft = engine.rewrite(&text_str, style);
    let json_out = serde_json::to_string(&draft).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json_out).unwrap().into_raw()
}

// ----------------------------------------------------------------------------
// RAMBLER JNI EXPORTS
// ----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_com_keyboard_engine_bridge_NativeBridge_ramblerProcessStream(
    mut env: JNIEnv,
    _class: JClass,
    raw_text: JString,
    is_final_pause: jboolean,
) -> jstring {
    let raw_str: String = match env.get_string(&raw_text) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let processor = rambler::RamblerTextProcessor::new(None);
    let cleaned = processor.process_raw_stream(&raw_str, is_final_pause != 0);
    env.new_string(cleaned).unwrap().into_raw()
}
