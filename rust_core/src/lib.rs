//! Agent Core - High-performance Rust core for AI Agent execution
//! 
//! This library provides high-performance implementations for:
//! - Image processing and pattern matching
//! - Game strategy calculation (eliminate games, pathfinding)
//! - Memory parsing and pattern search
//! - JNI bridge for Android integration

mod image_engine;
mod strategy_engine;
mod memory_engine;
mod jni_bridge;

pub use image_engine::*;
pub use strategy_engine::*;
pub use memory_engine::*;

use log::LevelFilter;
use android_logger::Config;

/// Initialize the Rust core library
pub fn init_library() {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Debug)
            .with_tag("AgentCore")
    );
    log::info!("Agent Core Rust library initialized");
}

/// Library version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version() {
        assert_eq!(VERSION, "1.0.0");
    }
}
