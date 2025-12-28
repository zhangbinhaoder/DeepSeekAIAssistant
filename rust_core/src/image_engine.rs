//! Image Processing Engine - High-performance image analysis
//! 
//! Provides:
//! - Pixel-level analysis and color detection
//! - Pattern matching for game elements
//! - HSV color space conversion
//! - Health bar / skill button detection

use rayon::prelude::*;
use rustc_hash::FxHashMap;
use serde::{Deserialize, Serialize};
use std::cmp::Ordering;

/// RGB color representation
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Rgb {
    pub r: u8,
    pub g: u8,
    pub b: u8,
}

impl Rgb {
    pub fn new(r: u8, g: u8, b: u8) -> Self {
        Self { r, g, b }
    }

    /// Calculate color distance (squared Euclidean)
    #[inline]
    pub fn distance_sq(&self, other: &Rgb) -> u32 {
        let dr = self.r as i32 - other.r as i32;
        let dg = self.g as i32 - other.g as i32;
        let db = self.b as i32 - other.b as i32;
        (dr * dr + dg * dg + db * db) as u32
    }

    /// Convert to HSV color space
    pub fn to_hsv(&self) -> Hsv {
        let r = self.r as f32 / 255.0;
        let g = self.g as f32 / 255.0;
        let b = self.b as f32 / 255.0;

        let max = r.max(g).max(b);
        let min = r.min(g).min(b);
        let delta = max - min;

        let h = if delta == 0.0 {
            0.0
        } else if max == r {
            60.0 * (((g - b) / delta) % 6.0)
        } else if max == g {
            60.0 * (((b - r) / delta) + 2.0)
        } else {
            60.0 * (((r - g) / delta) + 4.0)
        };

        let h = if h < 0.0 { h + 360.0 } else { h };
        let s = if max == 0.0 { 0.0 } else { delta / max };
        let v = max;

        Hsv { h, s, v }
    }

    /// Check if this color is within tolerance of another
    #[inline]
    pub fn matches(&self, other: &Rgb, tolerance: u32) -> bool {
        self.distance_sq(other) <= tolerance * tolerance
    }
}

/// HSV color representation
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct Hsv {
    pub h: f32, // 0-360
    pub s: f32, // 0-1
    pub v: f32, // 0-1
}

impl Hsv {
    /// Check if color is in red range (health bar - enemy)
    #[inline]
    pub fn is_red(&self) -> bool {
        (self.h < 15.0 || self.h > 345.0) && self.s > 0.5 && self.v > 0.3
    }

    /// Check if color is in blue range (health bar - ally)
    #[inline]
    pub fn is_blue(&self) -> bool {
        self.h > 200.0 && self.h < 260.0 && self.s > 0.5 && self.v > 0.3
    }

    /// Check if color is in green range (health bar - self)
    #[inline]
    pub fn is_green(&self) -> bool {
        self.h > 80.0 && self.h < 160.0 && self.s > 0.4 && self.v > 0.3
    }

    /// Check if color is bright/highlighted
    #[inline]
    pub fn is_bright(&self) -> bool {
        self.v > 0.7 && self.s < 0.3
    }
}

/// Rectangle region
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Rect {
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
}

impl Rect {
    pub fn new(x: i32, y: i32, width: i32, height: i32) -> Self {
        Self { x, y, width, height }
    }

    #[inline]
    pub fn center_x(&self) -> i32 {
        self.x + self.width / 2
    }

    #[inline]
    pub fn center_y(&self) -> i32 {
        self.y + self.height / 2
    }

    #[inline]
    pub fn contains(&self, px: i32, py: i32) -> bool {
        px >= self.x && px < self.x + self.width && py >= self.y && py < self.y + self.height
    }

    #[inline]
    pub fn area(&self) -> i32 {
        self.width * self.height
    }
}

/// Detected element in image
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DetectedElement {
    pub element_type: ElementType,
    pub bounds: Rect,
    pub confidence: f32,
    pub extra_data: Option<String>,
}

/// Types of detectable elements
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ElementType {
    HealthBarEnemy,
    HealthBarAlly,
    HealthBarSelf,
    SkillButton,
    Joystick,
    EliminateChess,
    Button,
    TextArea,
    Unknown,
}

/// Image data wrapper for processing
pub struct ImageData {
    pub width: usize,
    pub height: usize,
    pub pixels: Vec<Rgb>,
}

impl ImageData {
    /// Create from raw ARGB byte array (Android Bitmap format)
    pub fn from_argb_bytes(data: &[u8], width: usize, height: usize) -> Self {
        let mut pixels = Vec::with_capacity(width * height);
        for chunk in data.chunks_exact(4) {
            // ARGB format: [A, R, G, B]
            pixels.push(Rgb::new(chunk[1], chunk[2], chunk[3]));
        }
        Self { width, height, pixels }
    }

    /// Create from raw RGB byte array
    pub fn from_rgb_bytes(data: &[u8], width: usize, height: usize) -> Self {
        let mut pixels = Vec::with_capacity(width * height);
        for chunk in data.chunks_exact(3) {
            pixels.push(Rgb::new(chunk[0], chunk[1], chunk[2]));
        }
        Self { width, height, pixels }
    }

    /// Get pixel at coordinates
    #[inline]
    pub fn get_pixel(&self, x: usize, y: usize) -> Option<&Rgb> {
        if x < self.width && y < self.height {
            self.pixels.get(y * self.width + x)
        } else {
            None
        }
    }

    /// Get pixel at coordinates (unsafe, no bounds check)
    #[inline]
    pub unsafe fn get_pixel_unchecked(&self, x: usize, y: usize) -> &Rgb {
        self.pixels.get_unchecked(y * self.width + x)
    }
}

/// Image processing engine
pub struct ImageEngine;

impl ImageEngine {
    /// Detect health bars in image (parallel processing)
    pub fn detect_health_bars(image: &ImageData) -> Vec<DetectedElement> {
        let mut results = Vec::new();
        
        // Scan for horizontal colored bars
        // Health bars are typically 50-300px wide, 5-20px tall
        let min_bar_width = 50;
        let max_bar_height = 25;
        
        // Convert to HSV and find colored regions
        let hsv_image: Vec<Hsv> = image.pixels.par_iter()
            .map(|rgb| rgb.to_hsv())
            .collect();

        // Find red bars (enemy health)
        let red_regions = Self::find_colored_regions(&hsv_image, image.width, image.height, 
            |hsv| hsv.is_red(), min_bar_width, max_bar_height);
        for region in red_regions {
            results.push(DetectedElement {
                element_type: ElementType::HealthBarEnemy,
                bounds: region,
                confidence: 0.85,
                extra_data: None,
            });
        }

        // Find blue bars (ally health)
        let blue_regions = Self::find_colored_regions(&hsv_image, image.width, image.height,
            |hsv| hsv.is_blue(), min_bar_width, max_bar_height);
        for region in blue_regions {
            results.push(DetectedElement {
                element_type: ElementType::HealthBarAlly,
                bounds: region,
                confidence: 0.85,
                extra_data: None,
            });
        }

        // Find green bars (self health)
        let green_regions = Self::find_colored_regions(&hsv_image, image.width, image.height,
            |hsv| hsv.is_green(), min_bar_width, max_bar_height);
        for region in green_regions {
            results.push(DetectedElement {
                element_type: ElementType::HealthBarSelf,
                bounds: region,
                confidence: 0.85,
                extra_data: None,
            });
        }

        results
    }

    /// Find colored regions matching a predicate
    fn find_colored_regions<F>(
        hsv_image: &[Hsv],
        width: usize,
        height: usize,
        predicate: F,
        min_width: usize,
        max_height: usize,
    ) -> Vec<Rect>
    where
        F: Fn(&Hsv) -> bool + Sync,
    {
        let mut regions = Vec::new();
        let mut visited = vec![false; width * height];

        for y in 0..height {
            for x in 0..width {
                let idx = y * width + x;
                if visited[idx] {
                    continue;
                }

                let hsv = &hsv_image[idx];
                if !predicate(hsv) {
                    continue;
                }

                // Flood fill to find region bounds
                let mut min_x = x;
                let mut max_x = x;
                let mut min_y = y;
                let mut max_y = y;
                let mut stack = vec![(x, y)];

                while let Some((cx, cy)) = stack.pop() {
                    let cidx = cy * width + cx;
                    if visited[cidx] {
                        continue;
                    }
                    if !predicate(&hsv_image[cidx]) {
                        continue;
                    }

                    visited[cidx] = true;
                    min_x = min_x.min(cx);
                    max_x = max_x.max(cx);
                    min_y = min_y.min(cy);
                    max_y = max_y.max(cy);

                    // Add neighbors
                    if cx > 0 { stack.push((cx - 1, cy)); }
                    if cx + 1 < width { stack.push((cx + 1, cy)); }
                    if cy > 0 { stack.push((cx, cy - 1)); }
                    if cy + 1 < height { stack.push((cx, cy + 1)); }
                }

                let region_width = max_x - min_x + 1;
                let region_height = max_y - min_y + 1;

                // Filter by size constraints (health bars are wide and short)
                if region_width >= min_width && region_height <= max_height && region_width > region_height * 3 {
                    regions.push(Rect::new(
                        min_x as i32,
                        min_y as i32,
                        region_width as i32,
                        region_height as i32,
                    ));
                }
            }
        }

        regions
    }

    /// Detect skill buttons (circular/rounded elements in right side of screen)
    pub fn detect_skill_buttons(image: &ImageData) -> Vec<DetectedElement> {
        let mut results = Vec::new();
        
        // Skill buttons are typically in the right 1/3 of the screen
        let search_x_start = image.width * 2 / 3;
        
        // Look for bright circular regions
        let hsv_image: Vec<Hsv> = image.pixels.par_iter()
            .map(|rgb| rgb.to_hsv())
            .collect();

        // Find bright regions
        let bright_regions = Self::find_circular_regions(&hsv_image, image.width, image.height,
            search_x_start, 40, 120); // 40-120px diameter

        for region in bright_regions {
            results.push(DetectedElement {
                element_type: ElementType::SkillButton,
                bounds: region,
                confidence: 0.75,
                extra_data: None,
            });
        }

        results
    }

    /// Find approximately circular bright regions
    fn find_circular_regions(
        hsv_image: &[Hsv],
        width: usize,
        height: usize,
        x_start: usize,
        min_diameter: usize,
        max_diameter: usize,
    ) -> Vec<Rect> {
        let mut regions = Vec::new();
        let mut visited = vec![false; width * height];

        for y in 0..height {
            for x in x_start..width {
                let idx = y * width + x;
                if visited[idx] {
                    continue;
                }

                let hsv = &hsv_image[idx];
                if !hsv.is_bright() && hsv.s < 0.7 {
                    continue;
                }

                // Flood fill
                let mut min_x = x;
                let mut max_x = x;
                let mut min_y = y;
                let mut max_y = y;
                let mut pixel_count = 0;
                let mut stack = vec![(x, y)];

                while let Some((cx, cy)) = stack.pop() {
                    let cidx = cy * width + cx;
                    if visited[cidx] {
                        continue;
                    }

                    let chsv = &hsv_image[cidx];
                    if !chsv.is_bright() && chsv.s < 0.7 {
                        continue;
                    }

                    visited[cidx] = true;
                    pixel_count += 1;
                    min_x = min_x.min(cx);
                    max_x = max_x.max(cx);
                    min_y = min_y.min(cy);
                    max_y = max_y.max(cy);

                    if cx > 0 { stack.push((cx - 1, cy)); }
                    if cx + 1 < width { stack.push((cx + 1, cy)); }
                    if cy > 0 { stack.push((cx, cy - 1)); }
                    if cy + 1 < height { stack.push((cx, cy + 1)); }
                }

                let region_width = max_x - min_x + 1;
                let region_height = max_y - min_y + 1;
                let diameter = region_width.max(region_height);

                // Check if roughly circular and within size constraints
                let ratio = region_width as f32 / region_height as f32;
                let expected_area = std::f32::consts::PI * (diameter as f32 / 2.0).powi(2);
                let area_ratio = pixel_count as f32 / expected_area;

                if diameter >= min_diameter && diameter <= max_diameter
                    && ratio > 0.7 && ratio < 1.4  // Roughly square
                    && area_ratio > 0.5  // Filled enough
                {
                    regions.push(Rect::new(
                        min_x as i32,
                        min_y as i32,
                        region_width as i32,
                        region_height as i32,
                    ));
                }
            }
        }

        regions
    }

    /// Detect joystick (circular element in left side of screen)
    pub fn detect_joystick(image: &ImageData) -> Option<DetectedElement> {
        // Joystick is in the left 1/3, bottom half of screen
        let search_x_end = image.width / 3;
        let search_y_start = image.height / 2;

        let hsv_image: Vec<Hsv> = image.pixels.par_iter()
            .map(|rgb| rgb.to_hsv())
            .collect();

        // Look for large circular region (80-200px diameter)
        let mut visited = vec![false; image.width * image.height];
        let mut best_region: Option<Rect> = None;
        let mut best_area = 0;

        for y in search_y_start..image.height {
            for x in 0..search_x_end {
                let idx = y * image.width + x;
                if visited[idx] {
                    continue;
                }

                let hsv = &hsv_image[idx];
                // Joystick base is typically semi-transparent gray
                if hsv.v < 0.2 || hsv.v > 0.8 || hsv.s > 0.3 {
                    continue;
                }

                let mut min_x = x;
                let mut max_x = x;
                let mut min_y = y;
                let mut max_y = y;
                let mut stack = vec![(x, y)];

                while let Some((cx, cy)) = stack.pop() {
                    let cidx = cy * image.width + cx;
                    if visited[cidx] {
                        continue;
                    }

                    let chsv = &hsv_image[cidx];
                    if chsv.v < 0.2 || chsv.v > 0.8 || chsv.s > 0.3 {
                        continue;
                    }

                    visited[cidx] = true;
                    min_x = min_x.min(cx);
                    max_x = max_x.max(cx);
                    min_y = min_y.min(cy);
                    max_y = max_y.max(cy);

                    if cx > 0 { stack.push((cx - 1, cy)); }
                    if cx + 1 < image.width { stack.push((cx + 1, cy)); }
                    if cy > 0 { stack.push((cx, cy - 1)); }
                    if cy + 1 < image.height { stack.push((cx, cy + 1)); }
                }

                let region_width = max_x - min_x + 1;
                let region_height = max_y - min_y + 1;
                let area = region_width * region_height;
                let diameter = region_width.max(region_height);

                let ratio = region_width as f32 / region_height as f32;
                if diameter >= 80 && diameter <= 200 && ratio > 0.7 && ratio < 1.4 && area > best_area {
                    best_area = area;
                    best_region = Some(Rect::new(
                        min_x as i32,
                        min_y as i32,
                        region_width as i32,
                        region_height as i32,
                    ));
                }
            }
        }

        best_region.map(|bounds| DetectedElement {
            element_type: ElementType::Joystick,
            bounds,
            confidence: 0.80,
            extra_data: None,
        })
    }

    /// Analyze eliminate game board (like candy crush)
    /// Returns grid of chess piece colors
    pub fn analyze_eliminate_board(
        image: &ImageData,
        grid_bounds: &Rect,
        rows: usize,
        cols: usize,
    ) -> Vec<Vec<u8>> {
        let cell_width = grid_bounds.width as usize / cols;
        let cell_height = grid_bounds.height as usize / rows;

        let mut board = vec![vec![0u8; cols]; rows];

        // Parallel process each cell
        let results: Vec<((usize, usize), u8)> = (0..rows)
            .into_par_iter()
            .flat_map(|row| {
                (0..cols).into_par_iter().map(move |col| {
                    let cell_x = grid_bounds.x as usize + col * cell_width + cell_width / 2;
                    let cell_y = grid_bounds.y as usize + row * cell_height + cell_height / 2;
                    
                    // Sample center region of cell
                    let sample_size = 10;
                    let mut color_counts: FxHashMap<u8, usize> = FxHashMap::default();
                    
                    for dy in 0..sample_size {
                        for dx in 0..sample_size {
                            let px = cell_x + dx - sample_size / 2;
                            let py = cell_y + dy - sample_size / 2;
                            if let Some(rgb) = image.get_pixel(px, py) {
                                let color_id = Self::classify_chess_color(rgb);
                                *color_counts.entry(color_id).or_insert(0) += 1;
                            }
                        }
                    }

                    let dominant_color = color_counts
                        .into_iter()
                        .max_by_key(|(_, count)| *count)
                        .map(|(color, _)| color)
                        .unwrap_or(0);

                    ((row, col), dominant_color)
                })
            })
            .collect();

        for ((row, col), color) in results {
            board[row][col] = color;
        }

        board
    }

    /// Classify chess piece color into discrete categories
    fn classify_chess_color(rgb: &Rgb) -> u8 {
        let hsv = rgb.to_hsv();
        
        if hsv.v < 0.2 {
            return 0; // Empty/dark
        }

        // Classify by hue
        if hsv.h < 30.0 || hsv.h >= 330.0 {
            1 // Red
        } else if hsv.h < 60.0 {
            2 // Orange
        } else if hsv.h < 90.0 {
            3 // Yellow
        } else if hsv.h < 150.0 {
            4 // Green
        } else if hsv.h < 210.0 {
            5 // Cyan
        } else if hsv.h < 270.0 {
            6 // Blue
        } else {
            7 // Purple
        }
    }

    /// Find differences between two images (for detecting changes)
    pub fn find_differences(image1: &ImageData, image2: &ImageData, threshold: u32) -> Vec<Rect> {
        if image1.width != image2.width || image1.height != image2.height {
            return Vec::new();
        }

        let width = image1.width;
        let height = image1.height;
        
        // Find changed pixels
        let changed: Vec<bool> = image1.pixels.par_iter()
            .zip(image2.pixels.par_iter())
            .map(|(p1, p2)| p1.distance_sq(p2) > threshold * threshold)
            .collect();

        // Group changed pixels into regions
        let mut visited = vec![false; width * height];
        let mut regions = Vec::new();

        for y in 0..height {
            for x in 0..width {
                let idx = y * width + x;
                if visited[idx] || !changed[idx] {
                    continue;
                }

                let mut min_x = x;
                let mut max_x = x;
                let mut min_y = y;
                let mut max_y = y;
                let mut stack = vec![(x, y)];

                while let Some((cx, cy)) = stack.pop() {
                    let cidx = cy * width + cx;
                    if visited[cidx] || !changed[cidx] {
                        continue;
                    }

                    visited[cidx] = true;
                    min_x = min_x.min(cx);
                    max_x = max_x.max(cx);
                    min_y = min_y.min(cy);
                    max_y = max_y.max(cy);

                    if cx > 0 { stack.push((cx - 1, cy)); }
                    if cx + 1 < width { stack.push((cx + 1, cy)); }
                    if cy > 0 { stack.push((cx, cy - 1)); }
                    if cy + 1 < height { stack.push((cx, cy + 1)); }
                }

                let region_width = max_x - min_x + 1;
                let region_height = max_y - min_y + 1;

                // Only include significant changes
                if region_width > 10 && region_height > 10 {
                    regions.push(Rect::new(
                        min_x as i32,
                        min_y as i32,
                        region_width as i32,
                        region_height as i32,
                    ));
                }
            }
        }

        regions
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rgb_to_hsv() {
        let red = Rgb::new(255, 0, 0);
        let hsv = red.to_hsv();
        assert!((hsv.h - 0.0).abs() < 1.0);
        assert!((hsv.s - 1.0).abs() < 0.01);
        assert!((hsv.v - 1.0).abs() < 0.01);
    }

    #[test]
    fn test_color_distance() {
        let c1 = Rgb::new(100, 100, 100);
        let c2 = Rgb::new(100, 100, 100);
        assert_eq!(c1.distance_sq(&c2), 0);

        let c3 = Rgb::new(110, 100, 100);
        assert_eq!(c1.distance_sq(&c3), 100);
    }

    #[test]
    fn test_rect_operations() {
        let rect = Rect::new(10, 20, 100, 50);
        assert_eq!(rect.center_x(), 60);
        assert_eq!(rect.center_y(), 45);
        assert!(rect.contains(50, 30));
        assert!(!rect.contains(5, 30));
    }
}
