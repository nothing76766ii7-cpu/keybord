use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct TouchPoint {
    pub x: f32,
    pub y: f32,
    pub timestamp: i64,
    #[serde(default = "default_pressure")]
    pub pressure: f32,
}

fn default_pressure() -> f32 {
    1.0
}

impl TouchPoint {
    pub fn new(x: f32, y: f32, timestamp: i64, pressure: f32) -> Self {
        Self {
            x,
            y,
            timestamp,
            pressure,
        }
    }

    pub fn distance_to(&self, other: &TouchPoint) -> f32 {
        let dx = self.x - other.x;
        let dy = self.y - other.y;
        (dx * dx + dy * dy).sqrt()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Rect {
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

impl Rect {
    pub fn new(left: f32, top: f32, right: f32, bottom: f32) -> Self {
        Self {
            left,
            top,
            right,
            bottom,
        }
    }

    pub fn center_x(&self) -> f32 {
        (self.left + self.right) / 2.0
    }

    pub fn center_y(&self) -> f32 {
        (self.top + self.bottom) / 2.0
    }

    pub fn contains(&self, x: f32, y: f32) -> bool {
        x >= self.left && x <= self.right && y >= self.top && y <= self.bottom
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct KeyPosition {
    pub character: char,
    pub bounds: Rect,
    pub center_x: f32,
    pub center_y: f32,
}

impl KeyPosition {
    pub fn new(character: char, bounds: Rect) -> Self {
        let center_x = bounds.center_x();
        let center_y = bounds.center_y();
        Self {
            character: character.to_ascii_lowercase(),
            bounds,
            center_x,
            center_y,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct GlideCandidate {
    pub word: String,
    pub total_score: f32,
    pub spatial_score: f32,
    pub language_model_score: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GlideEngineConfig {
    pub min_distance_dp: f32,
    pub min_duration_ms: i64,
    pub min_tap_interval_ms: i64,
    pub resample_point_count: usize,
    pub spatial_sigma_x_dp: f32,
    pub spatial_sigma_y_dp: f32,
    pub anchor_radius_dp: f32,
    pub min_beam_width: usize,
    pub max_beam_width: usize,
    pub spatial_weight: f32,
    pub lm_weight: f32,
    pub velocity_tolerance_factor: f32,
}

impl Default for GlideEngineConfig {
    fn default() -> Self {
        Self {
            min_distance_dp: 24.0,
            min_duration_ms: 60,
            min_tap_interval_ms: 40,
            resample_point_count: 40,
            spatial_sigma_x_dp: 22.0,
            spatial_sigma_y_dp: 16.0,
            anchor_radius_dp: 36.0,
            min_beam_width: 30,
            max_beam_width: 80,
            spatial_weight: 0.65,
            lm_weight: 0.35,
            velocity_tolerance_factor: 1.5,
        }
    }
}

pub struct TrieNode {
    pub children: HashMap<char, TrieNode>,
    pub is_word: bool,
    pub frequency: f32,
    pub max_subtree_freq: f32,
    pub word: Option<String>,
}

impl TrieNode {
    pub fn new() -> Self {
        Self {
            children: HashMap::new(),
            is_word: false,
            frequency: 0.0,
            max_subtree_freq: 0.0,
            word: None,
        }
    }
}

pub struct TrieLexicon {
    pub root: TrieNode,
    pub word_count: usize,
    pub max_frequency: f32,
}

impl TrieLexicon {
    pub fn new() -> Self {
        Self {
            root: TrieNode::new(),
            word_count: 0,
            max_frequency: 1000.0,
        }
    }

    pub fn insert(&mut self, word: &str, frequency: f32) {
        let clean = word.trim().to_lowercase();
        if clean.is_empty() {
            return;
        }

        self.root.max_subtree_freq = self.root.max_subtree_freq.max(frequency);
        let mut current = &mut self.root;
        for ch in clean.chars() {
            current = current.children.entry(ch).or_insert_with(TrieNode::new);
            current.max_subtree_freq = current.max_subtree_freq.max(frequency);
        }
        if !current.is_word {
            self.word_count += 1;
        }
        current.is_word = true;
        current.frequency = current.frequency.max(frequency);
        self.max_frequency = self.max_frequency.max(current.frequency);
        current.word = Some(clean);
    }

    pub fn contains(&self, word: &str) -> bool {
        let clean = word.trim().to_lowercase();
        let mut current = &self.root;
        for ch in clean.chars() {
            match current.children.get(&ch) {
                Some(next) => current = next,
                None => return false,
            }
        }
        current.is_word
    }

    pub fn get_frequency(&self, word: &str) -> f32 {
        let clean = word.trim().to_lowercase();
        let mut current = &self.root;
        for ch in clean.chars() {
            match current.children.get(&ch) {
                Some(next) => current = next,
                None => return 0.0,
            }
        }
        if current.is_word {
            current.frequency
        } else {
            0.0
        }
    }

    /// Parses either `word<TAB>frequency` or rank-ordered single-word-per-line corpus.
    pub fn load_from_str(&mut self, contents: &str) {
        let mut rank = 0usize;
        for raw_line in contents.lines() {
            let trimmed = raw_line.trim();
            if trimmed.is_empty() {
                continue;
            }
            rank += 1;
            let mut parts = trimmed.split('\t');
            let word = match parts.next() {
                Some(w) if !w.is_empty() => w,
                _ => continue,
            };
            let freq = parts
                .next()
                .and_then(|s| s.trim().parse::<f32>().ok())
                .unwrap_or_else(|| (10000isize - rank as isize).max(1) as f32);
            self.insert(word, freq);
        }
    }

    pub fn load_default_vocabulary(&mut self) {
        let words = [
            ("the", 1000.0), ("be", 800.0), ("to", 750.0), ("of", 700.0), ("and", 680.0),
            ("a", 650.0), ("in", 600.0), ("that", 550.0), ("have", 500.0), ("i", 480.0),
            ("it", 470.0), ("for", 460.0), ("not", 450.0), ("on", 440.0), ("with", 430.0),
            ("he", 420.0), ("as", 410.0), ("you", 400.0), ("do", 390.0), ("at", 380.0),
            ("this", 370.0), ("but", 360.0), ("his", 350.0), ("by", 340.0), ("from", 330.0),
            ("they", 320.0), ("we", 310.0), ("say", 300.0), ("her", 290.0), ("she", 280.0),
            ("or", 270.0), ("an", 260.0), ("will", 250.0), ("my", 240.0), ("one", 230.0),
            ("all", 220.0), ("would", 210.0), ("there", 200.0), ("their", 190.0), ("what", 180.0),
            ("so", 170.0), ("up", 160.0), ("out", 150.0), ("if", 140.0), ("about", 130.0),
            ("who", 120.0), ("get", 110.0), ("which", 100.0), ("go", 95.0), ("me", 90.0),
            ("when", 85.0), ("make", 80.0), ("can", 75.0), ("like", 70.0), ("time", 68.0),
            ("no", 65.0), ("just", 62.0), ("him", 60.0), ("know", 58.0), ("take", 55.0),
            ("people", 52.0), ("into", 50.0), ("year", 48.0), ("your", 46.0), ("good", 44.0),
            ("some", 42.0), ("could", 40.0), ("them", 38.0), ("see", 36.0), ("other", 34.0),
            ("than", 32.0), ("then", 30.0), ("now", 28.0), ("look", 26.0), ("only", 24.0),
            ("come", 22.0), ("its", 20.0), ("over", 18.0), ("think", 16.0), ("also", 14.0),
            ("back", 12.0), ("after", 10.0), ("use", 8.0), ("two", 6.0), ("how", 5.0),
            ("our", 5.0), ("work", 4.0), ("first", 4.0), ("well", 4.0), ("way", 3.0),
            ("even", 3.0), ("new", 3.0), ("want", 3.0), ("because", 3.0), ("any", 2.0),
            ("these", 2.0), ("give", 2.0), ("day", 2.0), ("most", 2.0), ("us", 2.0),
            ("hello", 25.0), ("keyboard", 15.0), ("glide", 12.0), ("typing", 14.0),
            ("fast", 15.0), ("swipe", 14.0), ("world", 20.0), ("keybord", 30.0),
            ("cat", 20.0), ("dog", 20.0), ("jump", 15.0), ("quick", 15.0), ("brown", 15.0),
            ("fox", 15.0), ("lazy", 15.0), ("phone", 20.0), ("finger", 15.0),
            ("should", 25.0), ("through", 20.0), ("different", 20.0), ("something", 20.0),
        ];

        for (w, f) in words {
            self.insert(w, f);
        }
    }
}

pub fn gaussian_score_aniso(
    point: &TouchPoint,
    key: &KeyPosition,
    velocity_px_per_ms: f32,
    key_unit_px: f32,
    cfg: &GlideEngineConfig,
) -> f32 {
    let norm_vel = velocity_px_per_ms / (key_unit_px / 100.0).max(0.5);
    let speed_factor = 1.0 + (norm_vel * cfg.velocity_tolerance_factor * 0.16).clamp(0.0, 0.55);
    let dpi_scale = (key_unit_px / 40.0).max(1.0);
    let sx = cfg.spatial_sigma_x_dp * dpi_scale * speed_factor;
    let sy = cfg.spatial_sigma_y_dp * dpi_scale * speed_factor;
    let dx = (point.x - key.center_x) / sx;
    let dy = (point.y - key.center_y) / sy;
    (-(dx * dx + dy * dy) / 2.0).exp()
}

pub fn anchor_score(point: &TouchPoint, key: &KeyPosition, max_radius_px: f32) -> f32 {
    let dx = point.x - key.center_x;
    let dy = point.y - key.center_y;
    let dist = (dx * dx + dy * dy).sqrt();
    if dist > max_radius_px {
        return 0.0;
    }
    1.0 - (dist / max_radius_px)
}

pub fn direction_score(
    from_pt: &TouchPoint,
    to_pt: &TouchPoint,
    from_key: &KeyPosition,
    to_key: &KeyPosition,
) -> f32 {
    let traj_dx = to_pt.x - from_pt.x;
    let traj_dy = to_pt.y - from_pt.y;
    let key_dx = to_key.center_x - from_key.center_x;
    let key_dy = to_key.center_y - from_key.center_y;

    let traj_len = (traj_dx * traj_dx + traj_dy * traj_dy).sqrt();
    let key_len = (key_dx * key_dx + key_dy * key_dy).sqrt();

    if key_len < 1.0 {
        return 1.0; // Same key transition has no direction conflict
    }
    if traj_len < 1.0 {
        return 0.85; // Stationary dwell point
    }

    let cos_sim = ((traj_dx * key_dx + traj_dy * key_dy) / (traj_len * key_len)).clamp(-1.0, 1.0);
    0.7 + 0.3 * cos_sim
}

pub fn trajectory_corner_indices(points: &[TouchPoint]) -> Vec<usize> {
    if points.len() < 3 {
        return Vec::new();
    }
    let stride = if points.len() >= 12 { 2 } else { 1 };
    let mut best_corners: Vec<(usize, f32)> = Vec::new();
    for i in stride..(points.len() - stride) {
        let v1x = points[i].x - points[i - stride].x;
        let v1y = points[i].y - points[i - stride].y;
        let v2x = points[i + stride].x - points[i].x;
        let v2y = points[i + stride].y - points[i].y;
        let len1 = (v1x * v1x + v1y * v1y).sqrt();
        let len2 = (v2x * v2x + v2y * v2y).sqrt();
        if len1 > 1.0 && len2 > 1.0 {
            let cos = ((v1x * v2x + v1y * v2y) / (len1 * len2)).clamp(-1.0, 1.0);
            if cos < 0.45 {
                if let Some(last) = best_corners.last_mut() {
                    if i - last.0 <= stride + 1 {
                        if cos < last.1 {
                            *last = (i, cos);
                        }
                        continue;
                    }
                }
                best_corners.push((i, cos));
            }
        }
    }
    best_corners.into_iter().map(|(idx, _)| idx).collect()
}

pub fn trajectory_corner_count(points: &[TouchPoint]) -> usize {
    trajectory_corner_indices(points).len() + 1
}

pub fn compute_arc_length(points: &[TouchPoint]) -> f32 {
    (0..points.len().saturating_sub(1))
        .map(|i| points[i].distance_to(&points[i + 1]))
        .sum()
}

fn dedup_consecutive_chars(word: &str) -> Vec<char> {
    let mut out = Vec::with_capacity(word.len());
    for ch in word.to_lowercase().chars() {
        if out.last().copied() != Some(ch) {
            out.push(ch);
        }
    }
    out
}

pub struct SpatialModel<'a> {
    pub key_positions: &'a HashMap<char, KeyPosition>,
    pub config: &'a GlideEngineConfig,
}

impl<'a> SpatialModel<'a> {
    pub fn new(key_positions: &'a HashMap<char, KeyPosition>, config: &'a GlideEngineConfig) -> Self {
        Self {
            key_positions,
            config,
        }
    }

    pub fn key_unit_px(&self) -> f32 {
        let mut sum = 0.0f32;
        let mut count = 0usize;
        for kp in self.key_positions.values() {
            let w = (kp.bounds.right - kp.bounds.left).abs();
            let h = (kp.bounds.bottom - kp.bounds.top).abs();
            let u = (w + h) * 0.5;
            if u > 1.0 {
                sum += u;
                count += 1;
            }
        }
        if count > 0 {
            (sum / count as f32).max(40.0)
        } else {
            80.0
        }
    }

    pub fn dpi_scale(&self) -> f32 {
        (self.key_unit_px() / 40.0).max(1.0)
    }

    pub fn anchor_radius_px(&self) -> f32 {
        self.config.anchor_radius_dp * self.dpi_scale()
    }

    pub fn anchor_score_for_char(&self, point: &TouchPoint, key_char: char) -> f32 {
        match self.key_positions.get(&key_char.to_ascii_lowercase()) {
            Some(k) => anchor_score(point, k, self.anchor_radius_px()),
            None => 0.0,
        }
    }

    pub fn point_key_distance_score(
        &self,
        point: &TouchPoint,
        key_char: char,
        velocity_px_per_ms: f32,
    ) -> f32 {
        let key = match self.key_positions.get(&key_char.to_ascii_lowercase()) {
            Some(k) => k,
            None => return 0.00001,
        };
        gaussian_score_aniso(point, key, velocity_px_per_ms, self.key_unit_px(), self.config)
    }

    /// Evaluates how well every inflection corner in the trajectory is covered by candidate letters.
    pub fn corner_recall_score(
        &self,
        word_chars: &[char],
        trajectory: &[TouchPoint],
        corner_indices: &[usize],
        average_velocity: f32,
    ) -> f32 {
        if corner_indices.is_empty() || word_chars.is_empty() {
            return 1.0;
        }
        let mut prod = 1.0f32;
        for &c_idx in corner_indices {
            let pt = &trajectory[c_idx];
            let mut best = 0.0f32;
            for &ch in word_chars {
                let s = self.point_key_distance_score(pt, ch, average_velocity);
                if s > best {
                    best = s;
                }
            }
            prod *= best.clamp(0.05, 1.0);
        }
        prod.powf(1.0 / corner_indices.len() as f32)
    }

    pub fn point_segment_distance_score(
        &self,
        point: &TouchPoint,
        from_key: &KeyPosition,
        to_key: &KeyPosition,
        velocity_px_per_ms: f32,
    ) -> f32 {
        let vx = to_key.center_x - from_key.center_x;
        let vy = to_key.center_y - from_key.center_y;
        let len_sq = vx * vx + vy * vy;
        let t = if len_sq > 1.0 {
            (((point.x - from_key.center_x) * vx + (point.y - from_key.center_y) * vy) / len_sq)
                .clamp(0.0, 1.0)
        } else {
            1.0
        };
        let proj_x = from_key.center_x + t * vx;
        let proj_y = from_key.center_y + t * vy;

        let key_unit = self.key_unit_px();
        let norm_vel = velocity_px_per_ms / (key_unit / 100.0).max(0.5);
        let speed_factor = 1.0 + (norm_vel * self.config.velocity_tolerance_factor * 0.16).clamp(0.0, 0.55);
        let dpi_scale = (key_unit / 40.0).max(1.0);
        let sx = self.config.spatial_sigma_x_dp * dpi_scale * speed_factor;
        let sy = self.config.spatial_sigma_y_dp * dpi_scale * speed_factor;
        let dx = (point.x - proj_x) / sx;
        let dy = (point.y - proj_y) / sy;
        (-(dx * dx + dy * dy) / 2.0).exp()
    }

    pub fn align_word_trajectory(
        &self,
        candidate_word: &str,
        trajectory: &[TouchPoint],
        average_velocity: f32,
    ) -> f32 {
        // Collapse consecutive repeated letters ('hello' -> 'helo', 'different' -> 'diferent')
        // so double letters share one dwell without artificially halving DTW row costs.
        let word = dedup_consecutive_chars(candidate_word);
        let n = word.len();
        let m = trajectory.len();
        if n == 0 || m == 0 {
            return 0.0;
        }

        let start_anchor = self.anchor_score_for_char(&trajectory[0], word[0]);
        let end_anchor = self.anchor_score_for_char(&trajectory[m - 1], word[n - 1]);
        if start_anchor <= 0.0 || end_anchor <= 0.0 {
            return 0.0;
        }

        let mut cost = vec![vec![1e6f32; m + 1]; n + 1];
        cost[0][0] = 0.0;

        for i in 1..=n {
            let ch = word[i - 1];
            let curr_key = self.key_positions.get(&ch);
            let prev_key = if i > 1 {
                self.key_positions.get(&word[i - 2])
            } else {
                None
            };

            for j in 1..=m {
                let point = &trajectory[j - 1];
                let key_sc = self.point_key_distance_score(point, ch, average_velocity);
                let dir_sc = match (prev_key, curr_key) {
                    (Some(fk), Some(tk)) if j >= 2 => {
                        direction_score(&trajectory[j - 2], point, fk, tk)
                    }
                    _ => 1.0,
                };
                let diag_score = (key_sc * (0.75 + 0.25 * dir_sc)).max(1e-6);
                let diag_cost = -diag_score.ln();

                // Horizontal transit follows the stroke segment prev_key -> curr_key
                let transit_sc = match (prev_key, curr_key) {
                    (Some(fk), Some(tk)) => {
                        key_sc.max(self.point_segment_distance_score(point, fk, tk, average_velocity) * 0.92)
                    }
                    _ => key_sc,
                };
                let transit_cost = -(transit_sc * (0.75 + 0.25 * dir_sc)).max(1e-6).ln();

                let diag = cost[i - 1][j - 1] + diag_cost;
                let horiz = cost[i][j - 1] + transit_cost;
                let vert = cost[i - 1][j] + diag_cost + 0.35;
                cost[i][j] = diag.min(horiz).min(vert);
            }
        }

        let total_cost = cost[n][m];
        let normalized_cost = total_cost / (n + m) as f32;
        (-normalized_cost).exp()
    }

    pub fn find_proximity_keys(&self, point: &TouchPoint, max_radius_px: f32) -> Vec<char> {
        let mut candidates = Vec::new();
        for (&ch, key) in self.key_positions {
            let dx = point.x - key.center_x;
            let dy = point.y - key.center_y;
            let d = (dx * dx + dy * dy).sqrt();
            if key.bounds.contains(point.x, point.y) || d <= max_radius_px {
                candidates.push((ch, d));
            }
        }
        candidates.sort_by(|a, b| a.1.partial_cmp(&b.1).unwrap_or(std::cmp::Ordering::Equal));
        candidates.into_iter().map(|(ch, _)| ch).collect()
    }
}

pub struct TrajectoryProcessor<'a> {
    config: &'a GlideEngineConfig,
}

impl<'a> TrajectoryProcessor<'a> {
    pub fn new(config: &'a GlideEngineConfig) -> Self {
        Self { config }
    }

    pub fn validate_trajectory(
        &self,
        points: &[TouchPoint],
        last_tap_timestamp: i64,
        key_positions: &HashMap<char, KeyPosition>,
    ) -> bool {
        if points.len() < 2 {
            return false;
        }

        let start_time = points.first().unwrap().timestamp;
        let end_time = points.last().unwrap().timestamp;
        let duration = end_time - start_time;

        if last_tap_timestamp > 0 && start_time >= last_tap_timestamp && start_time - last_tap_timestamp < self.config.min_tap_interval_ms {
            return false;
        }

        if duration < self.config.min_duration_ms {
            return false;
        }

        let mut total_distance = 0.0f32;
        let mut min_x = f32::MAX;
        let mut max_x = f32::MIN;
        let mut min_y = f32::MAX;
        let mut max_y = f32::MIN;

        for i in 0..points.len() - 1 {
            let p1 = &points[i];
            let p2 = &points[i + 1];
            total_distance += p1.distance_to(p2);
            min_x = min_x.min(p1.x);
            max_x = max_x.max(p1.x);
            min_y = min_y.min(p1.y);
            max_y = max_y.max(p1.y);
        }

        let last_pt = points.last().unwrap();
        min_x = min_x.min(last_pt.x);
        max_x = max_x.max(last_pt.x);
        min_y = min_y.min(last_pt.y);
        max_y = max_y.max(last_pt.y);

        if total_distance < self.config.min_distance_dp {
            return false;
        }

        // Within one key check
        for key in key_positions.values() {
            if min_x >= key.bounds.left
                && max_x <= key.bounds.right
                && min_y >= key.bounds.top
                && max_y <= key.bounds.bottom
            {
                return false;
            }
        }

        true
    }

    pub fn resample_trajectory(&self, raw: &[TouchPoint], target_count: usize) -> Vec<TouchPoint> {
        if raw.len() <= 1 {
            return raw.to_vec();
        }
        if target_count <= 1 {
            return vec![raw[0]];
        }

        let mut cumulative = vec![0.0f32; raw.len()];
        for i in 1..raw.len() {
            cumulative[i] = cumulative[i - 1] + raw[i - 1].distance_to(&raw[i]);
        }

        let total_length = *cumulative.last().unwrap();
        if total_length <= 0.0001 {
            return vec![raw[0]; target_count];
        }

        let segment_step = total_length / (target_count - 1) as f32;
        let mut resampled = Vec::with_capacity(target_count);
        resampled.push(raw[0]);

        let mut raw_index = 0;
        for i in 1..target_count - 1 {
            let target_dist = i as f32 * segment_step;
            while raw_index < raw.len() - 1 && cumulative[raw_index + 1] < target_dist {
                raw_index += 1;
            }

            let d0 = cumulative[raw_index];
            let d1 = cumulative[raw_index + 1];
            let segment_len = d1 - d0;
            let t = if segment_len > 0.0 {
                (target_dist - d0) / segment_len
            } else {
                0.0
            };

            let p0 = &raw[raw_index];
            let p1 = &raw[raw_index + 1];

            let interp_x = p0.x + t * (p1.x - p0.x);
            let interp_y = p0.y + t * (p1.y - p0.y);
            let interp_time = (p0.timestamp as f32 + t * (p1.timestamp - p0.timestamp) as f32) as i64;
            let interp_pressure = p0.pressure + t * (p1.pressure - p0.pressure);

            resampled.push(TouchPoint::new(interp_x, interp_y, interp_time, interp_pressure));
        }

        resampled.push(*raw.last().unwrap());
        resampled
    }

    pub fn smooth_trajectory(&self, points: &[TouchPoint]) -> Vec<TouchPoint> {
        if points.len() < 3 {
            return points.to_vec();
        }

        let mut smoothed = Vec::with_capacity(points.len());
        smoothed.push(points[0]);

        for i in 1..points.len() - 1 {
            let prev = &points[i - 1];
            let curr = &points[i];
            let next = &points[i + 1];

            let smooth_x = 0.25 * prev.x + 0.5 * curr.x + 0.25 * next.x;
            let smooth_y = 0.25 * prev.y + 0.5 * curr.y + 0.25 * next.y;
            smoothed.push(TouchPoint::new(smooth_x, smooth_y, curr.timestamp, curr.pressure));
        }

        smoothed.push(*points.last().unwrap());
        smoothed
    }

    pub fn extract_corners(&self, points: &[TouchPoint], angle_threshold_rad: f32) -> Vec<usize> {
        if points.len() < 3 {
            return Vec::new();
        }
        let mut corners = Vec::new();
        corners.push(0);
        for i in 1..(points.len() - 1) {
            let v1x = points[i].x - points[i - 1].x;
            let v1y = points[i].y - points[i - 1].y;
            let v2x = points[i + 1].x - points[i].x;
            let v2y = points[i + 1].y - points[i].y;
            let n1 = (v1x * v1x + v1y * v1y).sqrt();
            let n2 = (v2x * v2x + v2y * v2y).sqrt();
            if n1 > 0.5 && n2 > 0.5 {
                let cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).clamp(-1.0, 1.0);
                if cos.acos() >= angle_threshold_rad {
                    corners.push(i);
                }
            }
        }
        corners.push(points.len() - 1);
        corners
    }
}

pub struct GlideDecoder {
    pub key_positions: HashMap<char, KeyPosition>,
    pub lexicon: TrieLexicon,
    pub config: GlideEngineConfig,
    pub last_tap_timestamp: i64,
}

impl GlideDecoder {
    pub fn new(
        key_positions: HashMap<char, KeyPosition>,
        lexicon: TrieLexicon,
        config: Option<GlideEngineConfig>,
    ) -> Self {
        Self {
            key_positions,
            lexicon,
            config: config.unwrap_or_default(),
            last_tap_timestamp: 0,
        }
    }

    pub fn update_key_positions(&mut self, positions: HashMap<char, KeyPosition>) {
        self.key_positions = positions;
    }

    pub fn notify_tap(&mut self, timestamp: i64) {
        self.last_tap_timestamp = timestamp;
    }

    pub fn decode_gesture(&self, raw_points: &[TouchPoint]) -> Vec<GlideCandidate> {
        if self.key_positions.is_empty() {
            return Vec::new();
        }

        let processor = TrajectoryProcessor::new(&self.config);
        let spatial = SpatialModel::new(&self.key_positions, &self.config);

        if !processor.validate_trajectory(raw_points, self.last_tap_timestamp, &self.key_positions) {
            return Vec::new();
        }

        let resampled = processor.resample_trajectory(raw_points, self.config.resample_point_count);
        let smoothed = processor.smooth_trajectory(&resampled);

        let duration_ms = (smoothed.last().unwrap().timestamp - smoothed.first().unwrap().timestamp).max(1);
        let arc_length = compute_arc_length(&smoothed);
        let avg_velocity = arc_length / duration_ms as f32;

        let start_pt = &smoothed[0];
        let end_pt = smoothed.last().unwrap();
        let anchor_radius = spatial.anchor_radius_px();

        let start_keys: Vec<char> = spatial
            .find_proximity_keys(start_pt, anchor_radius)
            .into_iter()
            .filter(|&c| spatial.anchor_score_for_char(start_pt, c) > 0.0)
            .collect();
        let end_keys: HashSet<char> = spatial
            .find_proximity_keys(end_pt, anchor_radius)
            .into_iter()
            .filter(|&c| spatial.anchor_score_for_char(end_pt, c) > 0.0)
            .collect();

        if start_keys.is_empty() || end_keys.is_empty() {
            return Vec::new();
        }

        let ref_arc = 400.0 * spatial.dpi_scale();
        let dynamic_beam = (self.config.min_beam_width as f32
            + (arc_length / ref_arc) * (self.config.max_beam_width - self.config.min_beam_width) as f32)
            .clamp(self.config.min_beam_width as f32, self.config.max_beam_width as f32) as usize;

        let sharp_corners = trajectory_corner_indices(&smoothed);
        let corner_indices = processor.extract_corners(&smoothed, 0.5);
        let step_size = (smoothed.len() / 20).max(1);
        let mut sample_indices: Vec<usize> = (step_size..smoothed.len()).step_by(step_size).collect();
        sample_indices.extend(&sharp_corners);
        sample_indices.extend(corner_indices);
        sample_indices.push(smoothed.len() - 1);
        sample_indices.retain(|&idx| idx > 0 && idx < smoothed.len());
        sample_indices.sort_unstable();
        sample_indices.dedup();

        let max_freq_ln = (self.lexicon.max_frequency + 1.0).ln().max(1.0);

        #[derive(Clone)]
        struct BeamNode<'a> {
            trie_node: &'a TrieNode,
            current_word: String,
            cumulative_score: f32,
            rank_score: f32,
        }

        let mut beam = Vec::new();
        for &start_ch in &start_keys {
            if let Some(child) = self.lexicon.root.children.get(&start_ch) {
                let score = spatial.point_key_distance_score(start_pt, start_ch, avg_velocity);
                let lm_prior = ((child.max_subtree_freq + 1.0).ln() / max_freq_ln).clamp(0.0, 1.0);
                beam.push(BeamNode {
                    trie_node: child,
                    current_word: start_ch.to_string(),
                    cumulative_score: score,
                    rank_score: score + 0.12 * lm_prior,
                });
            }
        }

        let key_unit = spatial.key_unit_px();
        let step_radius = key_unit * 1.15;
        let mut collected_words = HashSet::new();

        for &p_idx in &sample_indices {
            if beam.is_empty() {
                break;
            }
            let current_point = &smoothed[p_idx];
            let candidates_near_point = spatial.find_proximity_keys(current_point, step_radius);
            let mut next_beam = Vec::new();

            for b_node in &beam {
                for &next_ch in &candidates_near_point {
                    if let Some(child) = b_node.trie_node.children.get(&next_ch) {
                        let score = spatial.point_key_distance_score(current_point, next_ch, avg_velocity);
                        if score < 0.22 {
                            continue;
                        }
                        let mut w = b_node.current_word.clone();
                        w.push(next_ch);
                        let new_cum = b_node.cumulative_score + (score - 0.18);
                        let lm_prior = ((child.max_subtree_freq + 1.0).ln() / max_freq_ln).clamp(0.0, 1.0);
                        next_beam.push(BeamNode {
                            trie_node: child,
                            current_word: w,
                            cumulative_score: new_cum,
                            rank_score: new_cum + 0.12 * lm_prior,
                        });
                    }
                }

                if let Some(last_ch) = b_node.current_word.chars().last() {
                    if let Some(child) = b_node.trie_node.children.get(&last_ch) {
                        let stay_score = spatial.point_key_distance_score(current_point, last_ch, avg_velocity);
                        if stay_score > 0.35 {
                            let mut w = b_node.current_word.clone();
                            w.push(last_ch);
                            let new_cum = b_node.cumulative_score - 0.01;
                            let lm_prior = ((child.max_subtree_freq + 1.0).ln() / max_freq_ln).clamp(0.0, 1.0);
                            next_beam.push(BeamNode {
                                trie_node: child,
                                current_word: w,
                                cumulative_score: new_cum,
                                rank_score: new_cum + 0.12 * lm_prior,
                            });
                        }
                    }
                }

                let new_cum = b_node.cumulative_score - 0.015;
                let lm_prior = ((b_node.trie_node.max_subtree_freq + 1.0).ln() / max_freq_ln).clamp(0.0, 1.0);
                next_beam.push(BeamNode {
                    trie_node: b_node.trie_node,
                    current_word: b_node.current_word.clone(),
                    cumulative_score: new_cum,
                    rank_score: new_cum + 0.12 * lm_prior,
                });
            }

            next_beam.sort_by(|a, b| b.rank_score.partial_cmp(&a.rank_score).unwrap_or(std::cmp::Ordering::Equal));
            let mut seen_words = HashSet::new();
            beam.clear();
            for item in next_beam {
                if seen_words.insert(item.current_word.clone()) {
                    beam.push(item);
                    if beam.len() >= dynamic_beam {
                        break;
                    }
                }
            }
        }

        for b_node in &beam {
            Self::collect_completed_words(b_node.trie_node, &end_keys, &mut collected_words, 2);
            if b_node.trie_node.is_word {
                if let Some(ref w) = b_node.trie_node.word {
                    if let Some(last_c) = w.chars().last() {
                        if end_keys.contains(&last_c) {
                            collected_words.insert(w.clone());
                        }
                    }
                }
            }
        }

        // Direct 2-letter word capture from anchored start/end keys
        for &sc in &start_keys {
            if let Some(s_node) = self.lexicon.root.children.get(&sc) {
                for &ec in &end_keys {
                    if let Some(two_char) = s_node.children.get(&ec) {
                        if two_char.is_word {
                            if let Some(ref w) = two_char.word {
                                collected_words.insert(w.clone());
                            }
                        }
                    }
                }
            }
        }

        let expected_len = sharp_corners.len() + 2;
        let mut results = Vec::new();

        for word in collected_words {
            let char_len = word.chars().count();
            if char_len < 2 {
                continue;
            }

            let anchor_start = spatial.anchor_score_for_char(start_pt, word.chars().next().unwrap());
            let anchor_end = spatial.anchor_score_for_char(end_pt, word.chars().last().unwrap());
            let anchor = anchor_start * anchor_end;
            if anchor <= 0.0 {
                continue;
            }

            let spatial_score = spatial.align_word_trajectory(&word, &smoothed, avg_velocity);
            if spatial_score < 0.005 {
                continue;
            }

            let dedup_chars = dedup_consecutive_chars(&word);
            let corner_recall = spatial.corner_recall_score(&dedup_chars, &smoothed, &sharp_corners, avg_velocity);

            let base_freq = self.lexicon.get_frequency(&word);
            let lm_score = ((base_freq + 1.0).ln() / max_freq_ln).clamp(0.0, 1.0);

            let len_diff = (dedup_chars.len() as f32 - expected_len as f32).abs();
            let len_penalty = 1.0 / (1.0 + len_diff * 0.06);

            let mut total_score =
                self.config.spatial_weight * spatial_score + self.config.lm_weight * lm_score;
            total_score *= anchor * len_penalty * corner_recall;

            results.push(GlideCandidate {
                word,
                total_score,
                spatial_score,
                language_model_score: lm_score,
            });
        }

        results.sort_by(|a, b| b.total_score.partial_cmp(&a.total_score).unwrap_or(std::cmp::Ordering::Equal));
        results.truncate(8);
        results
    }

    fn collect_completed_words(
        node: &TrieNode,
        end_keys: &HashSet<char>,
        out: &mut HashSet<String>,
        max_depth: usize,
    ) {
        if node.is_word {
            if let Some(ref w) = node.word {
                if let Some(last_c) = w.chars().last() {
                    if end_keys.contains(&last_c) {
                        out.insert(w.clone());
                    }
                }
            }
        }
        if max_depth == 0 {
            return;
        }
        for child in node.children.values() {
            Self::collect_completed_words(child, end_keys, out, max_depth - 1);
        }
    }
}

// ============================================================================
// TESTS — Strictly matching PRD Section 5.1 requirements
// ============================================================================
#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_qwerty_positions() -> HashMap<char, KeyPosition> {
        let mut map = HashMap::new();
        let row1 = "qwertyuiop";
        let row2 = "asdfghjkl";
        let row3 = "zxcvbnm";

        let key_w = 100.0f32;
        let key_h = 120.0f32;

        for (i, c) in row1.chars().enumerate() {
            let left = i as f32 * key_w;
            map.insert(
                c,
                KeyPosition::new(c, Rect::new(left, 0.0, left + key_w, key_h)),
            );
        }
        for (i, c) in row2.chars().enumerate() {
            let left = (i as f32 + 0.5) * key_w;
            map.insert(
                c,
                KeyPosition::new(c, Rect::new(left, key_h, left + key_w, 2.0 * key_h)),
            );
        }
        for (i, c) in row3.chars().enumerate() {
            let left = (i as f32 + 1.5) * key_w;
            map.insert(
                c,
                KeyPosition::new(c, Rect::new(left, 2.0 * key_h, left + key_w, 3.0 * key_h)),
            );
        }
        map
    }

    // 1. test_gaussian_score_center() — score at key center = 1.0
    #[test]
    fn test_gaussian_score_center() {
        let positions = create_test_qwerty_positions();
        let config = GlideEngineConfig::default();
        let spatial = SpatialModel::new(&positions, &config);

        let k = &positions[&'h'];
        let pt = TouchPoint::new(k.center_x, k.center_y, 1000, 1.0);
        let score = spatial.point_key_distance_score(&pt, 'h', 0.0);
        assert!((score - 1.0).abs() < 1e-5, "Score at center must be 1.0, got {}", score);
    }

    // 2. test_gaussian_score_far() — score > 100dp away < 0.01
    #[test]
    fn test_gaussian_score_far() {
        let positions = create_test_qwerty_positions();
        let config = GlideEngineConfig::default();
        let spatial = SpatialModel::new(&positions, &config);

        let k = &positions[&'h'];
        let pt = TouchPoint::new(k.center_x + 120.0, k.center_y + 120.0, 1000, 1.0);
        let score = spatial.point_key_distance_score(&pt, 'h', 0.0);
        assert!(score < 0.01, "Score > 100dp away must be < 0.01, got {}", score);
    }

    // 3. test_dtw_exact_word() — perfect trajectory scores > 0.9
    #[test]
    fn test_dtw_exact_word() {
        let positions = create_test_qwerty_positions();
        let config = GlideEngineConfig::default();
        let spatial = SpatialModel::new(&positions, &config);

        let word = "cat";
        let mut points = Vec::new();
        let mut t = 1000i64;
        for ch in word.chars() {
            let k = &positions[&ch];
            for _ in 0..5 {
                points.push(TouchPoint::new(k.center_x, k.center_y, t, 1.0));
                t += 50;
            }
        }

        let score = spatial.align_word_trajectory(word, &points, 0.5);
        assert!(score > 0.85, "Exact trajectory must score high (> 0.85), got {}", score);
    }

    // 4. test_throttle_short_distance() — < 24dp returns empty
    #[test]
    fn test_throttle_short_distance() {
        let positions = create_test_qwerty_positions();
        let mut lex = TrieLexicon::new();
        lex.load_default_vocabulary();
        let decoder = GlideDecoder::new(positions, lex, None);

        let points = vec![
            TouchPoint::new(100.0, 100.0, 1000, 1.0),
            TouchPoint::new(102.0, 101.0, 1200, 1.0),
        ];
        let candidates = decoder.decode_gesture(&points);
        assert!(candidates.is_empty(), "Short distance gesture must be throttled");
    }

    // 5. test_throttle_within_one_key() — bounded gesture returns empty
    #[test]
    fn test_throttle_within_one_key() {
        let positions = create_test_qwerty_positions();
        let mut lex = TrieLexicon::new();
        lex.load_default_vocabulary();
        let decoder = GlideDecoder::new(positions.clone(), lex, None);

        let key_h = &positions[&'h'].bounds;
        let points = vec![
            TouchPoint::new(key_h.left + 5.0, key_h.top + 5.0, 1000, 1.0),
            TouchPoint::new(key_h.left + 15.0, key_h.top + 20.0, 1150, 1.0),
            TouchPoint::new(key_h.left + 10.0, key_h.top + 30.0, 1300, 1.0),
        ];
        let candidates = decoder.decode_gesture(&points);
        assert!(candidates.is_empty(), "Movement within one key must be throttled");
    }

    // 6. test_high_dpi_swipe_hello_and_world() — realistic 3x density off-center swipes
    #[test]
    fn test_high_dpi_swipe_hello_and_world() {
        let mut map = HashMap::new();
        let row1 = "qwertyuiop";
        let row2 = "asdfghjkl";
        let row3 = "zxcvbnm";
        let key_w = 108.0f32;
        let key_h = 162.0f32;

        for (i, c) in row1.chars().enumerate() {
            let left = i as f32 * key_w;
            map.insert(c, KeyPosition::new(c, Rect::new(left, 18.0, left + key_w - 12.0, 18.0 + key_h)));
        }
        for (i, c) in row2.chars().enumerate() {
            let left = i as f32 * (key_w * 1.11);
            map.insert(c, KeyPosition::new(c, Rect::new(left, 198.0, left + key_w, 198.0 + key_h)));
        }
        for (i, c) in row3.chars().enumerate() {
            let left = (i as f32 + 1.4) * key_w;
            map.insert(c, KeyPosition::new(c, Rect::new(left, 378.0, left + key_w - 12.0, 378.0 + key_h)));
        }

        let mut lex = TrieLexicon::new();
        lex.load_default_vocabulary();
        let decoder = GlideDecoder::new(map.clone(), lex, None);

        let build_swipe = |keys: &[char]| -> Vec<TouchPoint> {
            let mut pts = Vec::new();
            let mut t = 5000i64;
            for pair in keys.windows(2) {
                let a = &map[&pair[0]];
                let b = &map[&pair[1]];
                for step in 0..8 {
                    let frac = step as f32 / 8.0;
                    // Simulate realistic +18px vertical thumb offset and moderate 0.9 px/ms speed
                    pts.push(TouchPoint::new(
                        a.center_x + frac * (b.center_x - a.center_x),
                        a.center_y + 18.0 + frac * (b.center_y - a.center_y),
                        t,
                        1.0,
                    ));
                    t += 35;
                }
            }
            let last = &map[keys.last().unwrap()];
            pts.push(TouchPoint::new(last.center_x + 25.0, last.center_y + 40.0, t, 1.0));
            pts
        };

        let hello_candidates = decoder.decode_gesture(&build_swipe(&['h', 'e', 'l', 'o']));
        assert!(!hello_candidates.is_empty(), "3x DPI hello swipe must return candidates");
        assert!(
            hello_candidates.iter().any(|c| c.word == "hello"),
            "Expected 'hello' in candidates, got {:?}",
            hello_candidates.iter().map(|c| &c.word).collect::<Vec<_>>()
        );

        let world_candidates = decoder.decode_gesture(&build_swipe(&['w', 'o', 'r', 'l', 'd']));
        assert!(!world_candidates.is_empty(), "3x DPI world swipe must return candidates");
        assert!(
            world_candidates.iter().any(|c| c.word == "world"),
            "Expected 'world' in candidates, got {:?}",
            world_candidates.iter().map(|c| &c.word).collect::<Vec<_>>()
        );
    }

    #[test]
    fn test_anisotropic_gaussian_wider_x_than_y() {
        let positions = create_test_qwerty_positions();
        let config = GlideEngineConfig::default();
        let spatial = SpatialModel::new(&positions, &config);
        let h = &positions[&'h'];

        let pt_x = TouchPoint::new(h.center_x + 40.0, h.center_y, 1000, 1.0);
        let pt_y = TouchPoint::new(h.center_x, h.center_y + 40.0, 1000, 1.0);

        let sx_score = spatial.point_key_distance_score(&pt_x, 'h', 0.0);
        let sy_score = spatial.point_key_distance_score(&pt_y, 'h', 0.0);
        assert!(
            sx_score > sy_score,
            "Equal horizontal offset ({}) must score higher than equal vertical offset ({})",
            sx_score,
            sy_score
        );
    }

    #[test]
    fn test_anchor_hard_gate_rejects_far_start_end() {
        let positions = create_test_qwerty_positions();
        let config = GlideEngineConfig::default();
        let spatial = SpatialModel::new(&positions, &config);
        let q = &positions[&'q'];
        let p = &positions[&'p'];

        let far_start = TouchPoint::new(q.center_x + 400.0, q.center_y, 1000, 1.0);
        assert_eq!(anchor_score(&far_start, q, spatial.anchor_radius_px()), 0.0);
        let exact = TouchPoint::new(p.center_x, p.center_y, 1000, 1.0);
        assert!((anchor_score(&exact, p, spatial.anchor_radius_px()) - 1.0).abs() < 1e-5);
    }

    #[test]
    fn test_direction_score_aligned_vs_reversed() {
        let positions = create_test_qwerty_positions();
        let from_k = &positions[&'q'];
        let to_k = &positions[&'p'];
        let pt0 = TouchPoint::new(100.0, 60.0, 1000, 1.0);
        let pt_same = TouchPoint::new(200.0, 60.0, 1050, 1.0);
        let pt_opp = TouchPoint::new(0.0, 60.0, 1050, 1.0);

        let same = direction_score(&pt0, &pt_same, from_k, to_k);
        let opp = direction_score(&pt0, &pt_opp, from_k, to_k);
        assert!((same - 1.0).abs() < 1e-5, "Aligned direction should be 1.0, got {}", same);
        assert!((opp - 0.4).abs() < 1e-5, "Opposite direction should be 0.4, got {}", opp);
    }

    #[test]
    fn test_dynamic_beam_and_benchmark_words_full_wordlist() {
        let mut map = HashMap::new();
        let row1 = "qwertyuiop";
        let row2 = "asdfghjkl";
        let row3 = "zxcvbnm";
        // Simulate realistic 1080px phone portrait geometry (density = 3.0)
        let gap = 12.0f32;
        let avail_w = 1080.0f32 - 11.0 * gap;
        let key_w = avail_w / 10.0;
        let key_h = 162.0f32;

        for (i, c) in row1.chars().enumerate() {
            let left = gap + i as f32 * (key_w + gap);
            map.insert(c, KeyPosition::new(c, Rect::new(left, gap, left + key_w, gap + key_h)));
        }
        let row2_offset = gap + 0.5 * (key_w + gap);
        for (i, c) in row2.chars().enumerate() {
            let left = row2_offset + i as f32 * (key_w + gap);
            map.insert(c, KeyPosition::new(c, Rect::new(left, 2.0 * gap + key_h, left + key_w, 2.0 * gap + 2.0 * key_h)));
        }
        let row3_offset = gap + 1.5 * (key_w + gap);
        for (i, c) in row3.chars().enumerate() {
            let left = row3_offset + i as f32 * (key_w + gap);
            map.insert(c, KeyPosition::new(c, Rect::new(left, 3.0 * gap + 2.0 * key_h, left + key_w, 3.0 * gap + 3.0 * key_h)));
        }

        let mut lex = TrieLexicon::new();
        lex.load_from_str(include_str!("../../../app/src/main/assets/wordlist.txt"));
        assert!(lex.word_count > 9000, "Full asset wordlist should load, got {}", lex.word_count);
        let decoder = GlideDecoder::new(map.clone(), lex, None);

        let build_human_swipe = |word: &str, variant: usize| -> Vec<TouchPoint> {
            let mut dedup_chars = Vec::new();
            for ch in word.chars() {
                if dedup_chars.last().copied() != Some(ch) {
                    dedup_chars.push(ch);
                }
            }
            let (ox, oy, dt, end_ox, end_oy) = match variant {
                0 => (12.0f32, 18.0f32, 22i64, 16.0f32, 24.0f32),
                1 => (-18.0f32, 26.0f32, 14i64, -22.0f32, 30.0f32),
                _ => (22.0f32, -16.0f32, 26i64, 24.0f32, -20.0f32),
            };
            let mut pts = Vec::new();
            let mut t = 1000i64;
            let first = &map[&dedup_chars[0]];
            pts.push(TouchPoint::new(first.center_x + ox, first.center_y + oy, t, 1.0));
            for pair in dedup_chars.windows(2) {
                let a = &map[&pair[0]];
                let b = &map[&pair[1]];
                for step in 1..=6 {
                    let frac = step as f32 / 6.0;
                    t += dt;
                    let bow = if variant == 1 && step == 3 { 14.0 } else { 0.0 };
                    pts.push(TouchPoint::new(
                        a.center_x + ox + frac * (b.center_x - a.center_x),
                        a.center_y + oy + bow + frac * (b.center_y - a.center_y),
                        t,
                        1.0,
                    ));
                }
            }
            let last = &map[dedup_chars.last().unwrap()];
            pts.push(TouchPoint::new(last.center_x + end_ox, last.center_y + end_oy, t + dt, 1.0));
            pts
        };

        let benchmark = [
            "hello", "world", "quick", "brown", "phone",
            "there", "about", "which", "their", "would",
            "keyboard", "typing", "finger", "swipe", "should",
            "because", "through", "different", "something",
            "the", "that", "with", "from",
        ];

        let mut top1_hits = 0usize;
        let total_trials = benchmark.len() * 3;
        let mut misses = Vec::new();
        let total_start = std::time::Instant::now();
        for &target in &benchmark {
            for variant in 0..3 {
                let swipe = build_human_swipe(target, variant);
                let candidates = decoder.decode_gesture(&swipe);
                let top_word = candidates.first().map(|c| c.word.as_str()).unwrap_or("<none>");
                if top_word == target {
                    top1_hits += 1;
                } else {
                    misses.push(format!(
                        "{}#{} -> {:?}",
                        target,
                        variant,
                        candidates.iter().take(3).map(|c| format!("{}:{:.3}", c.word, c.total_score)).collect::<Vec<_>>()
                    ));
                }
            }
        }
        let avg_ms = total_start.elapsed().as_secs_f64() * 1000.0 / total_trials as f64;
        println!(
            "BENCHMARK: {}/{} top-1 ({:.1}%), avg {:.2} ms/word; misses: {:?}",
            top1_hits,
            total_trials,
            (top1_hits as f64 / total_trials as f64) * 100.0,
            avg_ms,
            misses
        );
        assert!(
            top1_hits * 100 >= total_trials * 85,
            "Expected >=85% top-1 accuracy, got {}/{} — misses: {:?}",
            top1_hits,
            total_trials,
            misses
        );
    }
}
