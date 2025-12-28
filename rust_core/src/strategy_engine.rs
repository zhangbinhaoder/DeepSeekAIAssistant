//! Strategy Calculation Engine - Game AI algorithms
//! 
//! Provides:
//! - Eliminate game optimal move finder (candy crush style)
//! - A* pathfinding for MOBA/RPG games
//! - Priority-based decision making

use crate::image_engine::Rect;
use priority_queue::PriorityQueue;
use rayon::prelude::*;
use rustc_hash::{FxHashMap, FxHashSet};
use serde::{Deserialize, Serialize};
use std::cmp::{Ordering, Reverse};

/// Move operation for eliminate games
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct EliminateMove {
    pub from_row: usize,
    pub from_col: usize,
    pub to_row: usize,
    pub to_col: usize,
    pub score: i32,
    pub eliminates: usize, // Number of pieces eliminated
    pub creates_special: bool, // Creates special piece (4+ match)
}

impl EliminateMove {
    pub fn new(from_row: usize, from_col: usize, to_row: usize, to_col: usize) -> Self {
        Self {
            from_row,
            from_col,
            to_row,
            to_col,
            score: 0,
            eliminates: 0,
            creates_special: false,
        }
    }
}

impl Ord for EliminateMove {
    fn cmp(&self, other: &Self) -> Ordering {
        // Prefer moves that create specials, then by score, then by eliminates
        self.creates_special.cmp(&other.creates_special)
            .then_with(|| self.score.cmp(&other.score))
            .then_with(|| self.eliminates.cmp(&other.eliminates))
    }
}

impl PartialOrd for EliminateMove {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

/// Eliminate game strategy engine
pub struct EliminateEngine;

impl EliminateEngine {
    /// Find all valid moves on the board
    pub fn find_all_moves(board: &[Vec<u8>]) -> Vec<EliminateMove> {
        let rows = board.len();
        if rows == 0 {
            return Vec::new();
        }
        let cols = board[0].len();

        let mut moves = Vec::new();

        // Check horizontal swaps
        for row in 0..rows {
            for col in 0..cols - 1 {
                if board[row][col] != board[row][col + 1] && board[row][col] != 0 && board[row][col + 1] != 0 {
                    let mut test_board = board.to_vec();
                    test_board[row].swap(col, col + 1);
                    
                    if let Some(mut mv) = Self::evaluate_move(&test_board, row, col, row, col + 1) {
                        mv.from_row = row;
                        mv.from_col = col;
                        mv.to_row = row;
                        mv.to_col = col + 1;
                        moves.push(mv);
                    }
                }
            }
        }

        // Check vertical swaps
        for row in 0..rows - 1 {
            for col in 0..cols {
                if board[row][col] != board[row + 1][col] && board[row][col] != 0 && board[row + 1][col] != 0 {
                    let mut test_board = board.to_vec();
                    let temp = test_board[row][col];
                    test_board[row][col] = test_board[row + 1][col];
                    test_board[row + 1][col] = temp;
                    
                    if let Some(mut mv) = Self::evaluate_move(&test_board, row, col, row + 1, col) {
                        mv.from_row = row;
                        mv.from_col = col;
                        mv.to_row = row + 1;
                        mv.to_col = col;
                        moves.push(mv);
                    }
                }
            }
        }

        moves
    }

    /// Evaluate a move and return its score
    fn evaluate_move(board: &[Vec<u8>], r1: usize, c1: usize, r2: usize, c2: usize) -> Option<EliminateMove> {
        let rows = board.len();
        let cols = board[0].len();
        
        let mut total_eliminates = 0;
        let mut creates_special = false;

        // Check matches at both swap positions
        for (row, col) in [(r1, c1), (r2, c2)] {
            let color = board[row][col];
            if color == 0 {
                continue;
            }

            // Check horizontal match
            let mut h_count = 1;
            let mut left = col;
            while left > 0 && board[row][left - 1] == color {
                left -= 1;
                h_count += 1;
            }
            let mut right = col;
            while right < cols - 1 && board[row][right + 1] == color {
                right += 1;
                h_count += 1;
            }

            // Check vertical match
            let mut v_count = 1;
            let mut top = row;
            while top > 0 && board[top - 1][col] == color {
                top -= 1;
                v_count += 1;
            }
            let mut bottom = row;
            while bottom < rows - 1 && board[bottom + 1][col] == color {
                bottom += 1;
                v_count += 1;
            }

            // Calculate eliminates
            if h_count >= 3 {
                total_eliminates += h_count;
                if h_count >= 4 {
                    creates_special = true;
                }
            }
            if v_count >= 3 {
                total_eliminates += v_count;
                if v_count >= 4 {
                    creates_special = true;
                }
            }

            // Cross pattern bonus
            if h_count >= 3 && v_count >= 3 {
                creates_special = true;
            }
        }

        if total_eliminates >= 3 {
            Some(EliminateMove {
                from_row: 0,
                from_col: 0,
                to_row: 0,
                to_col: 0,
                score: total_eliminates as i32 * 10 + if creates_special { 50 } else { 0 },
                eliminates: total_eliminates,
                creates_special,
            })
        } else {
            None
        }
    }

    /// Find the best move
    pub fn find_best_move(board: &[Vec<u8>]) -> Option<EliminateMove> {
        let moves = Self::find_all_moves(board);
        moves.into_iter().max()
    }

    /// Find top N best moves
    pub fn find_best_moves(board: &[Vec<u8>], n: usize) -> Vec<EliminateMove> {
        let mut moves = Self::find_all_moves(board);
        moves.sort_by(|a, b| b.cmp(a)); // Sort descending
        moves.truncate(n);
        moves
    }

    /// Simulate board after a move (for lookahead)
    pub fn simulate_move(board: &[Vec<u8>], mv: &EliminateMove) -> Vec<Vec<u8>> {
        let mut new_board = board.to_vec();
        
        // Swap pieces
        let temp = new_board[mv.from_row][mv.from_col];
        new_board[mv.from_row][mv.from_col] = new_board[mv.to_row][mv.to_col];
        new_board[mv.to_row][mv.to_col] = temp;

        // Remove matches and apply gravity (simplified)
        Self::remove_matches(&mut new_board);
        Self::apply_gravity(&mut new_board);

        new_board
    }

    fn remove_matches(board: &mut [Vec<u8>]) {
        let rows = board.len();
        let cols = board[0].len();
        let mut to_remove = vec![vec![false; cols]; rows];

        // Find horizontal matches
        for row in 0..rows {
            let mut start = 0;
            while start < cols {
                let color = board[row][start];
                if color == 0 {
                    start += 1;
                    continue;
                }

                let mut end = start;
                while end < cols && board[row][end] == color {
                    end += 1;
                }

                if end - start >= 3 {
                    for col in start..end {
                        to_remove[row][col] = true;
                    }
                }
                start = end;
            }
        }

        // Find vertical matches
        for col in 0..cols {
            let mut start = 0;
            while start < rows {
                let color = board[start][col];
                if color == 0 {
                    start += 1;
                    continue;
                }

                let mut end = start;
                while end < rows && board[end][col] == color {
                    end += 1;
                }

                if end - start >= 3 {
                    for row in start..end {
                        to_remove[row][col] = true;
                    }
                }
                start = end;
            }
        }

        // Remove marked pieces
        for row in 0..rows {
            for col in 0..cols {
                if to_remove[row][col] {
                    board[row][col] = 0;
                }
            }
        }
    }

    fn apply_gravity(board: &mut [Vec<u8>]) {
        let rows = board.len();
        let cols = board[0].len();

        for col in 0..cols {
            let mut write_row = rows;
            for read_row in (0..rows).rev() {
                if board[read_row][col] != 0 {
                    write_row -= 1;
                    if write_row != read_row {
                        board[write_row][col] = board[read_row][col];
                        board[read_row][col] = 0;
                    }
                }
            }
        }
    }
}

/// Position on a 2D grid
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct GridPos {
    pub x: i32,
    pub y: i32,
}

impl GridPos {
    pub fn new(x: i32, y: i32) -> Self {
        Self { x, y }
    }

    #[inline]
    pub fn manhattan_distance(&self, other: &GridPos) -> i32 {
        (self.x - other.x).abs() + (self.y - other.y).abs()
    }

    #[inline]
    pub fn euclidean_distance_sq(&self, other: &GridPos) -> i32 {
        let dx = self.x - other.x;
        let dy = self.y - other.y;
        dx * dx + dy * dy
    }
}

/// A* pathfinding result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PathResult {
    pub path: Vec<GridPos>,
    pub total_cost: i32,
    pub found: bool,
}

/// Pathfinding engine using A* algorithm
pub struct PathfindingEngine;

impl PathfindingEngine {
    /// Find path using A* algorithm
    /// - obstacles: set of blocked positions
    /// - grid_width/height: bounds of the grid
    pub fn find_path(
        start: GridPos,
        goal: GridPos,
        obstacles: &FxHashSet<GridPos>,
        grid_width: i32,
        grid_height: i32,
    ) -> PathResult {
        if start == goal {
            return PathResult {
                path: vec![start],
                total_cost: 0,
                found: true,
            };
        }

        // Check if goal is blocked
        if obstacles.contains(&goal) {
            return PathResult {
                path: Vec::new(),
                total_cost: -1,
                found: false,
            };
        }

        let mut open_set: PriorityQueue<GridPos, Reverse<i32>> = PriorityQueue::new();
        let mut came_from: FxHashMap<GridPos, GridPos> = FxHashMap::default();
        let mut g_score: FxHashMap<GridPos, i32> = FxHashMap::default();

        let h = |pos: &GridPos| pos.manhattan_distance(&goal);

        g_score.insert(start, 0);
        open_set.push(start, Reverse(h(&start)));

        // 4-directional movement
        let directions = [(0, 1), (0, -1), (1, 0), (-1, 0)];

        while let Some((current, _)) = open_set.pop() {
            if current == goal {
                // Reconstruct path
                let mut path = vec![current];
                let mut node = current;
                while let Some(&prev) = came_from.get(&node) {
                    path.push(prev);
                    node = prev;
                }
                path.reverse();

                return PathResult {
                    total_cost: *g_score.get(&current).unwrap_or(&0),
                    path,
                    found: true,
                };
            }

            let current_g = *g_score.get(&current).unwrap_or(&i32::MAX);

            for (dx, dy) in directions.iter() {
                let neighbor = GridPos::new(current.x + dx, current.y + dy);

                // Bounds check
                if neighbor.x < 0 || neighbor.x >= grid_width || neighbor.y < 0 || neighbor.y >= grid_height {
                    continue;
                }

                // Obstacle check
                if obstacles.contains(&neighbor) {
                    continue;
                }

                let tentative_g = current_g + 1; // Uniform cost

                if tentative_g < *g_score.get(&neighbor).unwrap_or(&i32::MAX) {
                    came_from.insert(neighbor, current);
                    g_score.insert(neighbor, tentative_g);
                    let f_score = tentative_g + h(&neighbor);
                    open_set.push(neighbor, Reverse(f_score));
                }
            }
        }

        PathResult {
            path: Vec::new(),
            total_cost: -1,
            found: false,
        }
    }

    /// Find path with 8-directional movement (diagonal allowed)
    pub fn find_path_8dir(
        start: GridPos,
        goal: GridPos,
        obstacles: &FxHashSet<GridPos>,
        grid_width: i32,
        grid_height: i32,
    ) -> PathResult {
        if start == goal {
            return PathResult {
                path: vec![start],
                total_cost: 0,
                found: true,
            };
        }

        if obstacles.contains(&goal) {
            return PathResult {
                path: Vec::new(),
                total_cost: -1,
                found: false,
            };
        }

        let mut open_set: PriorityQueue<GridPos, Reverse<i32>> = PriorityQueue::new();
        let mut came_from: FxHashMap<GridPos, GridPos> = FxHashMap::default();
        let mut g_score: FxHashMap<GridPos, i32> = FxHashMap::default();

        // Use Chebyshev distance for 8-dir
        let h = |pos: &GridPos| {
            let dx = (pos.x - goal.x).abs();
            let dy = (pos.y - goal.y).abs();
            dx.max(dy)
        };

        g_score.insert(start, 0);
        open_set.push(start, Reverse(h(&start)));

        // 8-directional movement with costs
        let directions = [
            (0, 1, 10),   // Up
            (0, -1, 10),  // Down
            (1, 0, 10),   // Right
            (-1, 0, 10),  // Left
            (1, 1, 14),   // Diagonal (sqrt(2) * 10 ≈ 14)
            (1, -1, 14),
            (-1, 1, 14),
            (-1, -1, 14),
        ];

        while let Some((current, _)) = open_set.pop() {
            if current == goal {
                let mut path = vec![current];
                let mut node = current;
                while let Some(&prev) = came_from.get(&node) {
                    path.push(prev);
                    node = prev;
                }
                path.reverse();

                return PathResult {
                    total_cost: *g_score.get(&current).unwrap_or(&0),
                    path,
                    found: true,
                };
            }

            let current_g = *g_score.get(&current).unwrap_or(&i32::MAX);

            for (dx, dy, cost) in directions.iter() {
                let neighbor = GridPos::new(current.x + dx, current.y + dy);

                if neighbor.x < 0 || neighbor.x >= grid_width || neighbor.y < 0 || neighbor.y >= grid_height {
                    continue;
                }

                if obstacles.contains(&neighbor) {
                    continue;
                }

                // For diagonal movement, check if adjacent cells are blocked
                if *dx != 0 && *dy != 0 {
                    let adj1 = GridPos::new(current.x + dx, current.y);
                    let adj2 = GridPos::new(current.x, current.y + dy);
                    if obstacles.contains(&adj1) || obstacles.contains(&adj2) {
                        continue; // Can't cut corners
                    }
                }

                let tentative_g = current_g + cost;

                if tentative_g < *g_score.get(&neighbor).unwrap_or(&i32::MAX) {
                    came_from.insert(neighbor, current);
                    g_score.insert(neighbor, tentative_g);
                    let f_score = tentative_g + h(&neighbor) * 10;
                    open_set.push(neighbor, Reverse(f_score));
                }
            }
        }

        PathResult {
            path: Vec::new(),
            total_cost: -1,
            found: false,
        }
    }

    /// Find nearest safe position (away from enemies)
    pub fn find_safe_position(
        current: GridPos,
        enemies: &[GridPos],
        obstacles: &FxHashSet<GridPos>,
        grid_width: i32,
        grid_height: i32,
        min_distance: i32,
    ) -> Option<GridPos> {
        // BFS to find nearest position that's far enough from all enemies
        let mut visited: FxHashSet<GridPos> = FxHashSet::default();
        let mut queue = vec![current];
        visited.insert(current);

        let directions = [(0, 1), (0, -1), (1, 0), (-1, 0)];

        while !queue.is_empty() {
            let mut next_queue = Vec::new();

            for pos in queue {
                // Check if this position is safe (far from all enemies)
                let is_safe = enemies.iter().all(|enemy| {
                    pos.manhattan_distance(enemy) >= min_distance
                });

                if is_safe && !obstacles.contains(&pos) {
                    return Some(pos);
                }

                // Add neighbors
                for (dx, dy) in directions.iter() {
                    let neighbor = GridPos::new(pos.x + dx, pos.y + dy);

                    if neighbor.x < 0 || neighbor.x >= grid_width 
                        || neighbor.y < 0 || neighbor.y >= grid_height {
                        continue;
                    }

                    if obstacles.contains(&neighbor) || visited.contains(&neighbor) {
                        continue;
                    }

                    visited.insert(neighbor);
                    next_queue.push(neighbor);
                }
            }

            queue = next_queue;
        }

        None
    }
}

/// Combat decision for MOBA games
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CombatDecision {
    pub action: CombatAction,
    pub target_pos: Option<GridPos>,
    pub priority: i32,
    pub reason: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CombatAction {
    Attack,
    UseSkill,
    Retreat,
    MoveToPosition,
    Wait,
}

/// Combat strategy engine for MOBA games
pub struct CombatEngine;

impl CombatEngine {
    /// Analyze combat situation and generate decisions
    pub fn analyze_combat(
        self_pos: GridPos,
        self_hp_percent: f32,
        enemies: &[(GridPos, f32)], // (position, hp_percent)
        allies: &[GridPos],
        skill_ready: &[bool],
        in_tower_range: bool,
    ) -> Vec<CombatDecision> {
        let mut decisions = Vec::new();

        // 1. Survival priority - retreat if low HP
        if self_hp_percent < 0.2 {
            decisions.push(CombatDecision {
                action: CombatAction::Retreat,
                target_pos: None,
                priority: 100,
                reason: "HP critical, must retreat".to_string(),
            });
            return decisions;
        }

        // 2. Tower safety
        if in_tower_range && allies.is_empty() {
            decisions.push(CombatDecision {
                action: CombatAction::Retreat,
                target_pos: None,
                priority: 90,
                reason: "In enemy tower range without allies".to_string(),
            });
            return decisions;
        }

        // 3. Find killable target (low HP enemy)
        let killable_enemies: Vec<_> = enemies.iter()
            .filter(|(pos, hp)| *hp < 0.3 && self_pos.manhattan_distance(pos) < 5)
            .collect();

        if !killable_enemies.is_empty() {
            let (target, _) = killable_enemies[0];
            decisions.push(CombatDecision {
                action: CombatAction::Attack,
                target_pos: Some(*target),
                priority: 80,
                reason: "Low HP enemy nearby".to_string(),
            });
        }

        // 4. Use skill if available and enemies nearby
        if skill_ready.get(0).copied().unwrap_or(false) && !enemies.is_empty() {
            let closest_enemy = enemies.iter()
                .min_by_key(|(pos, _)| self_pos.manhattan_distance(pos));
            
            if let Some((target, _)) = closest_enemy {
                if self_pos.manhattan_distance(target) < 6 {
                    decisions.push(CombatDecision {
                        action: CombatAction::UseSkill,
                        target_pos: Some(*target),
                        priority: 70,
                        reason: "Skill ready, enemy in range".to_string(),
                    });
                }
            }
        }

        // 5. Kite if outnumbered
        if enemies.len() > allies.len() + 1 && self_hp_percent < 0.5 {
            decisions.push(CombatDecision {
                action: CombatAction::Retreat,
                target_pos: None,
                priority: 60,
                reason: "Outnumbered with low HP".to_string(),
            });
        }

        // 6. Default: move to optimal position
        if decisions.is_empty() {
            decisions.push(CombatDecision {
                action: CombatAction::Wait,
                target_pos: None,
                priority: 10,
                reason: "No immediate action needed".to_string(),
            });
        }

        // Sort by priority
        decisions.sort_by(|a, b| b.priority.cmp(&a.priority));
        decisions
    }

    /// Calculate optimal attack position (maintain distance while attacking)
    pub fn calculate_kite_position(
        self_pos: GridPos,
        target_pos: GridPos,
        attack_range: i32,
        obstacles: &FxHashSet<GridPos>,
        grid_width: i32,
        grid_height: i32,
    ) -> Option<GridPos> {
        let current_dist = self_pos.manhattan_distance(&target_pos);
        
        // If already at optimal range, stay
        if current_dist == attack_range {
            return Some(self_pos);
        }

        // Find position at attack range
        let directions = [(0, 1), (0, -1), (1, 0), (-1, 0)];
        let mut best_pos = None;
        let mut best_diff = i32::MAX;

        for (dx, dy) in directions.iter() {
            let new_pos = GridPos::new(self_pos.x + dx, self_pos.y + dy);
            
            if new_pos.x < 0 || new_pos.x >= grid_width || new_pos.y < 0 || new_pos.y >= grid_height {
                continue;
            }
            
            if obstacles.contains(&new_pos) {
                continue;
            }

            let new_dist = new_pos.manhattan_distance(&target_pos);
            let diff = (new_dist - attack_range).abs();

            if diff < best_diff {
                best_diff = diff;
                best_pos = Some(new_pos);
            }
        }

        best_pos
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_eliminate_find_moves() {
        let board = vec![
            vec![1, 1, 2, 3, 4],
            vec![2, 2, 2, 4, 5],
            vec![3, 3, 3, 5, 6],
            vec![4, 4, 4, 6, 1],
            vec![5, 5, 5, 1, 2],
        ];

        let moves = EliminateEngine::find_all_moves(&board);
        assert!(!moves.is_empty());
    }

    #[test]
    fn test_pathfinding() {
        let start = GridPos::new(0, 0);
        let goal = GridPos::new(5, 5);
        let obstacles = FxHashSet::default();

        let result = PathfindingEngine::find_path(start, goal, &obstacles, 10, 10);
        assert!(result.found);
        assert_eq!(result.path.first(), Some(&start));
        assert_eq!(result.path.last(), Some(&goal));
    }

    #[test]
    fn test_pathfinding_with_obstacles() {
        let start = GridPos::new(0, 0);
        let goal = GridPos::new(2, 0);
        let mut obstacles = FxHashSet::default();
        obstacles.insert(GridPos::new(1, 0)); // Block direct path

        let result = PathfindingEngine::find_path(start, goal, &obstacles, 10, 10);
        assert!(result.found);
        assert!(result.path.len() > 3); // Must go around
    }

    #[test]
    fn test_combat_analysis() {
        let self_pos = GridPos::new(5, 5);
        let enemies = vec![(GridPos::new(7, 5), 0.8)];
        let allies = vec![GridPos::new(4, 5)];
        let skill_ready = vec![true, false, false];

        let decisions = CombatEngine::analyze_combat(
            self_pos,
            0.7,
            &enemies,
            &allies,
            &skill_ready,
            false,
        );

        assert!(!decisions.is_empty());
    }
}
