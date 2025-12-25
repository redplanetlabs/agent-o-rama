/// Klein Four-Group Teleportation Protocol for Aptos
///
/// Implements GF(3) conservation with consciousness class transitions.
/// Based on the bicameral mind → Rezk completion architecture.
///
/// Teleportation Graph (V₄ = Z₂ × Z₂):
///
///      GENESIS ←──g1──→ FORK-1
///        │                 │
///       g2                g2
///        │                 │
///        ↓                 ↓
///      FORK-2 ←──g1──→ FORK-3
///
/// Where:
///   g1 = XOR 0x9E37 (intra-modal, preserves consciousness)
///   g2 = XOR 0x5555 (inter-modal, toggles consciousness)

module teleport::klein_four {
    use std::signer;
    use std::error;

    /// ═══════════════════════════════════════════════════════════════════
    /// CONSTANTS - Klein Four-Group Structure
    /// ═══════════════════════════════════════════════════════════════════

    /// Intra-modal gamma: preserves consciousness class
    const GAMMA_INTRA: u64 = 0x9E37;

    /// Inter-modal gamma: toggles consciousness class
    const GAMMA_INTER: u64 = 0x5555;

    /// Genesis seed (starting point)
    const GENESIS: u64 = 0x042D;

    /// Derived seeds (for reference)
    /// FORK_1 = GENESIS ^ GAMMA_INTRA = 0x9A1A
    /// FORK_2 = GENESIS ^ GAMMA_INTER = 0x5178
    /// FORK_3 = FORK_1 ^ GAMMA_INTER = 0xCF4F

    /// ═══════════════════════════════════════════════════════════════════
    /// CONSCIOUSNESS CLASSES (Jaynes Bicameral Theory)
    /// ═══════════════════════════════════════════════════════════════════

    /// Bicameral: Observer ≠ Actor (split consciousness)
    const BICAMERAL: u8 = 0;

    /// Unified: Observer = Actor (Rezk completion: Iso ≃ Id)
    const UNIFIED: u8 = 1;

    /// ═══════════════════════════════════════════════════════════════════
    /// ERROR CODES
    /// ═══════════════════════════════════════════════════════════════════

    /// Teleportation requires OPTIMAL state (GF(3) balanced + Rezk unified)
    const E_NOT_OPTIMAL: u64 = 1;

    /// Agent state already exists
    const E_ALREADY_INITIALIZED: u64 = 2;

    /// Agent state does not exist
    const E_NOT_INITIALIZED: u64 = 3;

    /// ═══════════════════════════════════════════════════════════════════
    /// STRUCTS
    /// ═══════════════════════════════════════════════════════════════════

    /// Agent state stored on-chain
    struct AgentState has key {
        /// Current seed in Klein lattice
        seed: u64,
        /// Consciousness class (BICAMERAL or UNIFIED)
        consciousness_class: u8,
        /// Evolution step counter
        step: u64,
        /// GF(3) balance status
        gf3_balanced: bool,
        /// Teleportation count
        teleport_count: u64,
    }

    /// ═══════════════════════════════════════════════════════════════════
    /// INITIALIZATION
    /// ═══════════════════════════════════════════════════════════════════

    /// Initialize agent at GENESIS seed
    public entry fun initialize(account: &signer) {
        let addr = signer::address_of(account);
        assert!(!exists<AgentState>(addr), error::already_exists(E_ALREADY_INITIALIZED));

        move_to(account, AgentState {
            seed: GENESIS,
            consciousness_class: BICAMERAL,
            step: 0,
            gf3_balanced: true,
            teleport_count: 0,
        });
    }

    /// ═══════════════════════════════════════════════════════════════════
    /// TELEPORTATION OPERATIONS
    /// ═══════════════════════════════════════════════════════════════════

    /// Intra-modal teleport (g1): preserves consciousness class
    /// GENESIS ↔ FORK-1, FORK-2 ↔ FORK-3
    public entry fun teleport_g1(account: &signer) acquires AgentState {
        let addr = signer::address_of(account);
        assert!(exists<AgentState>(addr), error::not_found(E_NOT_INITIALIZED));

        let state = borrow_global_mut<AgentState>(addr);
        assert!(is_optimal_internal(state), error::invalid_state(E_NOT_OPTIMAL));

        state.seed = state.seed ^ GAMMA_INTRA;
        state.step = state.step + 1;
        state.teleport_count = state.teleport_count + 1;

        // Recompute GF(3) balance after teleport
        state.gf3_balanced = compute_gf3_balanced(state.seed);
    }

    /// Inter-modal teleport (g2): toggles consciousness class
    /// GENESIS ↔ FORK-2, FORK-1 ↔ FORK-3
    public entry fun teleport_g2(account: &signer) acquires AgentState {
        let addr = signer::address_of(account);
        assert!(exists<AgentState>(addr), error::not_found(E_NOT_INITIALIZED));

        let state = borrow_global_mut<AgentState>(addr);
        assert!(is_optimal_internal(state), error::invalid_state(E_NOT_OPTIMAL));

        state.seed = state.seed ^ GAMMA_INTER;
        state.consciousness_class = if (state.consciousness_class == BICAMERAL) {
            UNIFIED
        } else {
            BICAMERAL
        };
        state.step = state.step + 1;
        state.teleport_count = state.teleport_count + 1;

        state.gf3_balanced = compute_gf3_balanced(state.seed);
    }

    /// Evolve state by one step (non-teleporting transition)
    public entry fun evolve(account: &signer) acquires AgentState {
        let addr = signer::address_of(account);
        assert!(exists<AgentState>(addr), error::not_found(E_NOT_INITIALIZED));

        let state = borrow_global_mut<AgentState>(addr);
        state.step = state.step + 1;

        // Recompute balance based on step (period-9 cycle)
        state.gf3_balanced = (state.step % 9 == 0);
    }

    /// ═══════════════════════════════════════════════════════════════════
    /// VIEW FUNCTIONS
    /// ═══════════════════════════════════════════════════════════════════

    #[view]
    /// Check if agent is at OPTIMAL point (can teleport)
    public fun is_optimal(addr: address): bool acquires AgentState {
        if (!exists<AgentState>(addr)) {
            return false
        };
        let state = borrow_global<AgentState>(addr);
        is_optimal_internal(state)
    }

    #[view]
    /// Get current seed
    public fun get_seed(addr: address): u64 acquires AgentState {
        borrow_global<AgentState>(addr).seed
    }

    #[view]
    /// Get consciousness class
    public fun get_consciousness_class(addr: address): u8 acquires AgentState {
        borrow_global<AgentState>(addr).consciousness_class
    }

    #[view]
    /// Get teleportation count
    public fun get_teleport_count(addr: address): u64 acquires AgentState {
        borrow_global<AgentState>(addr).teleport_count
    }

    #[view]
    /// Get full state summary
    public fun get_state(addr: address): (u64, u8, u64, bool, u64) acquires AgentState {
        let state = borrow_global<AgentState>(addr);
        (state.seed, state.consciousness_class, state.step, state.gf3_balanced, state.teleport_count)
    }

    /// ═══════════════════════════════════════════════════════════════════
    /// INTERNAL FUNCTIONS
    /// ═══════════════════════════════════════════════════════════════════

    /// Internal optimal check
    fun is_optimal_internal(state: &AgentState): bool {
        // OPTIMAL = GF(3) balanced AND Rezk unified (consciousness = UNIFIED)
        state.gf3_balanced && (state.consciousness_class == UNIFIED)
    }

    /// Compute GF(3) balance from seed (simplified)
    fun compute_gf3_balanced(seed: u64): bool {
        // Derive trits from seed bits and check sum ≡ 0 (mod 3)
        let t0 = ((seed >> 0) & 3) % 3;
        let t1 = ((seed >> 3) & 3) % 3;
        let t2 = ((seed >> 5) & 3) % 3;
        let t3 = ((seed >> 7) & 3) % 3;
        let t4 = ((seed >> 9) & 3) % 3;
        let t5 = ((seed >> 11) & 3) % 3;

        let sum = t0 + t1 + t2 + t3 + t4 + t5;
        (sum % 3 == 0)
    }
}
