//! Memory Parsing Engine - Game memory data extraction
//! 
//! Provides:
//! - Memory map parsing (/proc/pid/maps)
//! - Pattern searching in memory regions
//! - Game data structure parsing

use memmap2::MmapOptions;
use regex::bytes::Regex;
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::{BufRead, BufReader, Read};
use rayon::prelude::*;

/// Memory region information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryRegion {
    pub start_addr: u64,
    pub end_addr: u64,
    pub permissions: String,
    pub offset: u64,
    pub device: String,
    pub inode: u64,
    pub pathname: String,
}

impl MemoryRegion {
    /// Check if region is readable
    pub fn is_readable(&self) -> bool {
        self.permissions.starts_with('r')
    }

    /// Check if region is writable
    pub fn is_writable(&self) -> bool {
        self.permissions.chars().nth(1) == Some('w')
    }

    /// Check if region is executable
    pub fn is_executable(&self) -> bool {
        self.permissions.chars().nth(2) == Some('x')
    }

    /// Get region size
    pub fn size(&self) -> u64 {
        self.end_addr - self.start_addr
    }

    /// Check if region belongs to a heap
    pub fn is_heap(&self) -> bool {
        self.pathname.contains("[heap]")
    }

    /// Check if region belongs to a stack
    pub fn is_stack(&self) -> bool {
        self.pathname.contains("[stack]")
    }

    /// Check if region is anonymous (no file backing)
    pub fn is_anonymous(&self) -> bool {
        self.pathname.is_empty() || self.pathname == "[anon]"
    }
}

/// Pattern match result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PatternMatch {
    pub address: u64,
    pub region_start: u64,
    pub offset_in_region: u64,
    pub matched_bytes: Vec<u8>,
}

/// Game data value types
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum GameValue {
    Int32(i32),
    Int64(i64),
    Float32(f32),
    Float64(f64),
    String(String),
    Bytes(Vec<u8>),
}

/// Parsed game data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GameData {
    pub name: String,
    pub address: u64,
    pub value: GameValue,
}

/// Memory parsing engine
pub struct MemoryEngine;

impl MemoryEngine {
    /// Parse /proc/pid/maps to get memory regions
    pub fn parse_memory_maps(pid: u32) -> Result<Vec<MemoryRegion>, String> {
        let maps_path = format!("/proc/{}/maps", pid);
        let file = File::open(&maps_path)
            .map_err(|e| format!("Failed to open {}: {}", maps_path, e))?;

        let reader = BufReader::new(file);
        let mut regions = Vec::new();

        for line in reader.lines() {
            let line = line.map_err(|e| format!("Failed to read line: {}", e))?;
            if let Some(region) = Self::parse_maps_line(&line) {
                regions.push(region);
            }
        }

        Ok(regions)
    }

    /// Parse a single line from /proc/pid/maps
    fn parse_maps_line(line: &str) -> Option<MemoryRegion> {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() < 5 {
            return None;
        }

        // Parse address range
        let addr_parts: Vec<&str> = parts[0].split('-').collect();
        if addr_parts.len() != 2 {
            return None;
        }

        let start_addr = u64::from_str_radix(addr_parts[0], 16).ok()?;
        let end_addr = u64::from_str_radix(addr_parts[1], 16).ok()?;

        // Parse permissions
        let permissions = parts[1].to_string();

        // Parse offset
        let offset = u64::from_str_radix(parts[2], 16).unwrap_or(0);

        // Parse device
        let device = parts[3].to_string();

        // Parse inode
        let inode = parts[4].parse().unwrap_or(0);

        // Parse pathname (may be empty or span multiple spaces)
        let pathname = if parts.len() > 5 {
            parts[5..].join(" ")
        } else {
            String::new()
        };

        Some(MemoryRegion {
            start_addr,
            end_addr,
            permissions,
            offset,
            device,
            inode,
            pathname,
        })
    }

    /// Search for byte pattern in memory
    pub fn search_pattern(
        pid: u32,
        pattern: &[u8],
        regions: &[MemoryRegion],
        limit: usize,
    ) -> Result<Vec<PatternMatch>, String> {
        let mem_path = format!("/proc/{}/mem", pid);
        let mut file = File::open(&mem_path)
            .map_err(|e| format!("Failed to open {}: {}", mem_path, e))?;

        let mut matches = Vec::new();
        let pattern_len = pattern.len();

        for region in regions {
            if !region.is_readable() || region.size() == 0 {
                continue;
            }

            // Read region data
            let mut buffer = vec![0u8; region.size() as usize];
            
            // Seek to region start
            use std::io::Seek;
            if file.seek(std::io::SeekFrom::Start(region.start_addr)).is_err() {
                continue;
            }

            // Read region
            if file.read_exact(&mut buffer).is_err() {
                continue;
            }

            // Search for pattern in buffer
            for (i, window) in buffer.windows(pattern_len).enumerate() {
                if window == pattern {
                    matches.push(PatternMatch {
                        address: region.start_addr + i as u64,
                        region_start: region.start_addr,
                        offset_in_region: i as u64,
                        matched_bytes: window.to_vec(),
                    });

                    if matches.len() >= limit {
                        return Ok(matches);
                    }
                }
            }
        }

        Ok(matches)
    }

    /// Search for pattern with wildcards (mask-based search)
    pub fn search_pattern_masked(
        pid: u32,
        pattern: &[u8],
        mask: &[bool], // true = must match, false = wildcard
        regions: &[MemoryRegion],
        limit: usize,
    ) -> Result<Vec<PatternMatch>, String> {
        if pattern.len() != mask.len() {
            return Err("Pattern and mask length mismatch".to_string());
        }

        let mem_path = format!("/proc/{}/mem", pid);
        let mut file = File::open(&mem_path)
            .map_err(|e| format!("Failed to open {}: {}", mem_path, e))?;

        let mut matches = Vec::new();
        let pattern_len = pattern.len();

        for region in regions {
            if !region.is_readable() || region.size() == 0 {
                continue;
            }

            let mut buffer = vec![0u8; region.size() as usize];
            
            use std::io::Seek;
            if file.seek(std::io::SeekFrom::Start(region.start_addr)).is_err() {
                continue;
            }

            if file.read_exact(&mut buffer).is_err() {
                continue;
            }

            // Search with mask
            'outer: for i in 0..buffer.len().saturating_sub(pattern_len - 1) {
                for j in 0..pattern_len {
                    if mask[j] && buffer[i + j] != pattern[j] {
                        continue 'outer;
                    }
                }

                matches.push(PatternMatch {
                    address: region.start_addr + i as u64,
                    region_start: region.start_addr,
                    offset_in_region: i as u64,
                    matched_bytes: buffer[i..i + pattern_len].to_vec(),
                });

                if matches.len() >= limit {
                    return Ok(matches);
                }
            }
        }

        Ok(matches)
    }

    /// Search for 32-bit integer value
    pub fn search_int32(
        pid: u32,
        value: i32,
        regions: &[MemoryRegion],
        limit: usize,
    ) -> Result<Vec<PatternMatch>, String> {
        Self::search_pattern(pid, &value.to_le_bytes(), regions, limit)
    }

    /// Search for 32-bit float value (with tolerance)
    pub fn search_float32(
        pid: u32,
        value: f32,
        tolerance: f32,
        regions: &[MemoryRegion],
        limit: usize,
    ) -> Result<Vec<PatternMatch>, String> {
        let mem_path = format!("/proc/{}/mem", pid);
        let mut file = File::open(&mem_path)
            .map_err(|e| format!("Failed to open {}: {}", mem_path, e))?;

        let mut matches = Vec::new();

        for region in regions {
            if !region.is_readable() || region.size() < 4 {
                continue;
            }

            let mut buffer = vec![0u8; region.size() as usize];
            
            use std::io::Seek;
            if file.seek(std::io::SeekFrom::Start(region.start_addr)).is_err() {
                continue;
            }

            if file.read_exact(&mut buffer).is_err() {
                continue;
            }

            // Search for float values
            for i in (0..buffer.len() - 3).step_by(4) {
                let bytes: [u8; 4] = buffer[i..i + 4].try_into().unwrap();
                let found_value = f32::from_le_bytes(bytes);

                if (found_value - value).abs() <= tolerance && found_value.is_finite() {
                    matches.push(PatternMatch {
                        address: region.start_addr + i as u64,
                        region_start: region.start_addr,
                        offset_in_region: i as u64,
                        matched_bytes: bytes.to_vec(),
                    });

                    if matches.len() >= limit {
                        return Ok(matches);
                    }
                }
            }
        }

        Ok(matches)
    }

    /// Read value at specific address
    pub fn read_value(pid: u32, address: u64, size: usize) -> Result<Vec<u8>, String> {
        let mem_path = format!("/proc/{}/mem", pid);
        let mut file = File::open(&mem_path)
            .map_err(|e| format!("Failed to open {}: {}", mem_path, e))?;

        use std::io::Seek;
        file.seek(std::io::SeekFrom::Start(address))
            .map_err(|e| format!("Failed to seek: {}", e))?;

        let mut buffer = vec![0u8; size];
        file.read_exact(&mut buffer)
            .map_err(|e| format!("Failed to read: {}", e))?;

        Ok(buffer)
    }

    /// Read 32-bit integer at address
    pub fn read_int32(pid: u32, address: u64) -> Result<i32, String> {
        let bytes = Self::read_value(pid, address, 4)?;
        let arr: [u8; 4] = bytes.try_into().map_err(|_| "Invalid byte count")?;
        Ok(i32::from_le_bytes(arr))
    }

    /// Read 32-bit float at address
    pub fn read_float32(pid: u32, address: u64) -> Result<f32, String> {
        let bytes = Self::read_value(pid, address, 4)?;
        let arr: [u8; 4] = bytes.try_into().map_err(|_| "Invalid byte count")?;
        Ok(f32::from_le_bytes(arr))
    }

    /// Read null-terminated string at address
    pub fn read_string(pid: u32, address: u64, max_len: usize) -> Result<String, String> {
        let bytes = Self::read_value(pid, address, max_len)?;
        let null_pos = bytes.iter().position(|&b| b == 0).unwrap_or(bytes.len());
        String::from_utf8(bytes[..null_pos].to_vec())
            .map_err(|e| format!("Invalid UTF-8: {}", e))
    }

    /// Filter regions by common game memory patterns
    pub fn filter_game_regions(regions: &[MemoryRegion]) -> Vec<MemoryRegion> {
        regions.iter()
            .filter(|r| {
                // Keep readable, writable anonymous regions (heap-like)
                r.is_readable() && r.is_writable() && 
                (r.is_anonymous() || r.is_heap()) &&
                r.size() > 4096 && r.size() < 512 * 1024 * 1024 // 4KB - 512MB
            })
            .cloned()
            .collect()
    }

    /// Find regions belonging to a specific library
    pub fn find_library_regions(regions: &[MemoryRegion], lib_name: &str) -> Vec<MemoryRegion> {
        regions.iter()
            .filter(|r| r.pathname.contains(lib_name))
            .cloned()
            .collect()
    }

    /// Calculate pointer chain (for multi-level pointer)
    pub fn resolve_pointer_chain(
        pid: u32,
        base_address: u64,
        offsets: &[u64],
    ) -> Result<u64, String> {
        let mut address = base_address;

        for (i, &offset) in offsets.iter().enumerate() {
            // Read pointer at current address
            let bytes = Self::read_value(pid, address, 8)?;
            let arr: [u8; 8] = bytes.try_into().map_err(|_| "Invalid byte count")?;
            let ptr = u64::from_le_bytes(arr);

            if ptr == 0 {
                return Err(format!("Null pointer at offset index {}", i));
            }

            address = ptr + offset;
        }

        Ok(address)
    }
}

/// Common game data structures
pub struct GameDataStructures;

impl GameDataStructures {
    /// Parse Unity player stats structure
    /// Typical layout: HP (float), MaxHP (float), MP (float), MaxMP (float)
    pub fn parse_unity_stats(data: &[u8]) -> Option<(f32, f32, f32, f32)> {
        if data.len() < 16 {
            return None;
        }

        let hp = f32::from_le_bytes(data[0..4].try_into().ok()?);
        let max_hp = f32::from_le_bytes(data[4..8].try_into().ok()?);
        let mp = f32::from_le_bytes(data[8..12].try_into().ok()?);
        let max_mp = f32::from_le_bytes(data[12..16].try_into().ok()?);

        // Sanity check
        if hp >= 0.0 && hp <= max_hp && max_hp > 0.0 && max_hp < 100000.0 {
            Some((hp, max_hp, mp, max_mp))
        } else {
            None
        }
    }

    /// Parse position structure (x, y, z as floats)
    pub fn parse_position(data: &[u8]) -> Option<(f32, f32, f32)> {
        if data.len() < 12 {
            return None;
        }

        let x = f32::from_le_bytes(data[0..4].try_into().ok()?);
        let y = f32::from_le_bytes(data[4..8].try_into().ok()?);
        let z = f32::from_le_bytes(data[8..12].try_into().ok()?);

        // Sanity check - reasonable world coordinates
        if x.is_finite() && y.is_finite() && z.is_finite() &&
            x.abs() < 100000.0 && y.abs() < 100000.0 && z.abs() < 100000.0 {
            Some((x, y, z))
        } else {
            None
        }
    }

    /// Parse skill cooldown structure
    pub fn parse_skill_cooldowns(data: &[u8], skill_count: usize) -> Vec<f32> {
        let mut cooldowns = Vec::with_capacity(skill_count);
        
        for i in 0..skill_count {
            let offset = i * 4;
            if offset + 4 > data.len() {
                break;
            }
            
            if let Ok(arr) = data[offset..offset + 4].try_into() {
                let cd: f32 = f32::from_le_bytes(arr);
                if cd.is_finite() && cd >= 0.0 && cd < 1000.0 {
                    cooldowns.push(cd);
                }
            }
        }

        cooldowns
    }
}

/// Memory signature for common games
#[derive(Debug, Clone)]
pub struct GameSignature {
    pub game_name: String,
    pub package_name: String,
    pub hp_pattern: Vec<u8>,
    pub hp_mask: Vec<bool>,
    pub hp_offset: i64,
    pub position_pattern: Vec<u8>,
    pub position_mask: Vec<bool>,
    pub position_offset: i64,
}

impl GameSignature {
    /// Create signature for a generic Unity game
    pub fn generic_unity() -> Self {
        Self {
            game_name: "Generic Unity Game".to_string(),
            package_name: String::new(),
            // Unity float pattern for HP (look for reasonable HP values)
            hp_pattern: vec![0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00],
            hp_mask: vec![false, false, false, false, false, false, false, false],
            hp_offset: 0,
            position_pattern: vec![0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00],
            position_mask: vec![false; 12],
            position_offset: 0,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_maps_line() {
        let line = "7f1234567000-7f1234568000 r-xp 00000000 08:01 12345 /lib/libc.so";
        let region = MemoryEngine::parse_maps_line(line).unwrap();
        
        assert_eq!(region.start_addr, 0x7f1234567000);
        assert_eq!(region.end_addr, 0x7f1234568000);
        assert_eq!(region.permissions, "r-xp");
        assert!(region.is_readable());
        assert!(!region.is_writable());
        assert!(region.is_executable());
    }

    #[test]
    fn test_parse_unity_stats() {
        // HP=100.0, MaxHP=100.0, MP=50.0, MaxMP=100.0
        let data = [
            0x00, 0x00, 0xC8, 0x42, // 100.0f
            0x00, 0x00, 0xC8, 0x42, // 100.0f
            0x00, 0x00, 0x48, 0x42, // 50.0f
            0x00, 0x00, 0xC8, 0x42, // 100.0f
        ];

        let stats = GameDataStructures::parse_unity_stats(&data).unwrap();
        assert!((stats.0 - 100.0).abs() < 0.01);
        assert!((stats.1 - 100.0).abs() < 0.01);
        assert!((stats.2 - 50.0).abs() < 0.01);
    }

    #[test]
    fn test_parse_position() {
        // x=10.0, y=20.0, z=30.0
        let data = [
            0x00, 0x00, 0x20, 0x41, // 10.0f
            0x00, 0x00, 0xA0, 0x41, // 20.0f
            0x00, 0x00, 0xF0, 0x41, // 30.0f
        ];

        let pos = GameDataStructures::parse_position(&data).unwrap();
        assert!((pos.0 - 10.0).abs() < 0.01);
        assert!((pos.1 - 20.0).abs() < 0.01);
        assert!((pos.2 - 30.0).abs() < 0.01);
    }

    #[test]
    fn test_region_filters() {
        let region = MemoryRegion {
            start_addr: 0x1000,
            end_addr: 0x10000,
            permissions: "rw-p".to_string(),
            offset: 0,
            device: "00:00".to_string(),
            inode: 0,
            pathname: "[heap]".to_string(),
        };

        assert!(region.is_readable());
        assert!(region.is_writable());
        assert!(!region.is_executable());
        assert!(region.is_heap());
        assert!(!region.is_stack());
    }
}
