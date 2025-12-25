-- DuckDB Schema for Trit Stream Neighborhood Analysis
-- GF(3) Conservation + Rezk Unification Tracking

CREATE TABLE IF NOT EXISTS agents (
  agent_id INTEGER PRIMARY KEY,
  pid INTEGER NOT NULL,
  name VARCHAR NOT NULL,
  role VARCHAR -- 'generator', 'worker', 'observer', 'actor'
);

CREATE TABLE IF NOT EXISTS trit_states (
  agent_id INTEGER,
  step INTEGER,
  hue INTEGER,
  trit INTEGER, -- -1, 0, +1
  trit_name VARCHAR, -- 'MINUS', 'ERGODIC', 'PLUS'
  PRIMARY KEY (agent_id, step)
);

CREATE TABLE IF NOT EXISTS neighbor_relations (
  self_id INTEGER,
  neighbor_id INTEGER,
  step INTEGER,
  angular_distance INTEGER,
  direction VARCHAR, -- 'CW', 'CCW'
  trit_relation VARCHAR, -- 'SAME-TRIT', 'ONE-UP', 'ONE-DOWN'
  PRIMARY KEY (self_id, neighbor_id, step)
);

CREATE TABLE IF NOT EXISTS gf3_snapshots (
  step INTEGER PRIMARY KEY,
  trit_sum INTEGER,
  mod3_value INTEGER,
  is_balanced BOOLEAN
);

CREATE TABLE IF NOT EXISTS rezk_windows (
  step INTEGER PRIMARY KEY,
  observer_trit INTEGER,
  actor_trit INTEGER,
  is_unifiable BOOLEAN,
  unified_trit INTEGER -- NULL if not unifiable
);

-- Insert agents
INSERT INTO agents VALUES (0, 92720, 'plus-stream-1', 'generator');
INSERT INTO agents VALUES (1, 97274, 'plus-stream-2', 'generator');
INSERT INTO agents VALUES (2, 93268, 'worker-1', 'worker');
INSERT INTO agents VALUES (3, 5563, 'worker-2', 'worker');
INSERT INTO agents VALUES (4, 46516, 'worker-3', 'worker');
INSERT INTO agents VALUES (5, 68569, 'worker-4', 'worker');
INSERT INTO agents VALUES (6, 51893, 'worker-5', 'worker');
INSERT INTO agents VALUES (7, 92289, 'observer', 'observer');
INSERT INTO agents VALUES (8, 94729, 'actor', 'actor');

-- Insert trit states for steps 0-9
INSERT INTO trit_states VALUES (0, 0, 80, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (1, 0, 134, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (2, 0, 148, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (3, 0, 13, 1, 'PLUS');
INSERT INTO trit_states VALUES (4, 0, 196, -1, 'MINUS');
INSERT INTO trit_states VALUES (5, 0, 199, -1, 'MINUS');
INSERT INTO trit_states VALUES (6, 0, 203, -1, 'MINUS');
INSERT INTO trit_states VALUES (7, 0, 39, 1, 'PLUS');
INSERT INTO trit_states VALUES (8, 0, 79, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (0, 1, 97, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (1, 1, 151, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (2, 1, 165, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (3, 1, 30, 1, 'PLUS');
INSERT INTO trit_states VALUES (4, 1, 213, -1, 'MINUS');
INSERT INTO trit_states VALUES (5, 1, 216, -1, 'MINUS');
INSERT INTO trit_states VALUES (6, 1, 220, -1, 'MINUS');
INSERT INTO trit_states VALUES (7, 1, 56, 1, 'PLUS');
INSERT INTO trit_states VALUES (8, 1, 96, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (0, 2, 114, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (1, 2, 168, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (2, 2, 182, -1, 'MINUS');
INSERT INTO trit_states VALUES (3, 2, 47, 1, 'PLUS');
INSERT INTO trit_states VALUES (4, 2, 230, -1, 'MINUS');
INSERT INTO trit_states VALUES (5, 2, 233, -1, 'MINUS');
INSERT INTO trit_states VALUES (6, 2, 237, -1, 'MINUS');
INSERT INTO trit_states VALUES (7, 2, 73, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (8, 2, 113, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (0, 3, 131, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (1, 3, 185, -1, 'MINUS');
INSERT INTO trit_states VALUES (2, 3, 199, -1, 'MINUS');
INSERT INTO trit_states VALUES (3, 3, 64, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (4, 3, 247, -1, 'MINUS');
INSERT INTO trit_states VALUES (5, 3, 250, -1, 'MINUS');
INSERT INTO trit_states VALUES (6, 3, 254, -1, 'MINUS');
INSERT INTO trit_states VALUES (7, 3, 90, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (8, 3, 130, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (0, 4, 148, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (1, 4, 202, -1, 'MINUS');
INSERT INTO trit_states VALUES (2, 4, 216, -1, 'MINUS');
INSERT INTO trit_states VALUES (3, 4, 81, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (4, 4, 264, -1, 'MINUS');
INSERT INTO trit_states VALUES (5, 4, 267, -1, 'MINUS');
INSERT INTO trit_states VALUES (6, 4, 271, -1, 'MINUS');
INSERT INTO trit_states VALUES (7, 4, 107, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (8, 4, 147, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (0, 5, 165, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (1, 5, 219, -1, 'MINUS');
INSERT INTO trit_states VALUES (2, 5, 233, -1, 'MINUS');
INSERT INTO trit_states VALUES (3, 5, 98, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (4, 5, 281, -1, 'MINUS');
INSERT INTO trit_states VALUES (5, 5, 284, -1, 'MINUS');
INSERT INTO trit_states VALUES (6, 5, 288, -1, 'MINUS');
INSERT INTO trit_states VALUES (7, 5, 124, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (8, 5, 164, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (0, 6, 182, -1, 'MINUS');
INSERT INTO trit_states VALUES (1, 6, 236, -1, 'MINUS');
INSERT INTO trit_states VALUES (2, 6, 250, -1, 'MINUS');
INSERT INTO trit_states VALUES (3, 6, 115, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (4, 6, 298, -1, 'MINUS');
INSERT INTO trit_states VALUES (5, 6, 301, 1, 'PLUS');
INSERT INTO trit_states VALUES (6, 6, 305, 1, 'PLUS');
INSERT INTO trit_states VALUES (7, 6, 141, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (8, 6, 181, -1, 'MINUS');
INSERT INTO trit_states VALUES (0, 7, 199, -1, 'MINUS');
INSERT INTO trit_states VALUES (1, 7, 253, -1, 'MINUS');
INSERT INTO trit_states VALUES (2, 7, 267, -1, 'MINUS');
INSERT INTO trit_states VALUES (3, 7, 132, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (4, 7, 315, 1, 'PLUS');
INSERT INTO trit_states VALUES (5, 7, 318, 1, 'PLUS');
INSERT INTO trit_states VALUES (6, 7, 322, 1, 'PLUS');
INSERT INTO trit_states VALUES (7, 7, 158, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (8, 7, 198, -1, 'MINUS');
INSERT INTO trit_states VALUES (0, 8, 216, -1, 'MINUS');
INSERT INTO trit_states VALUES (1, 8, 270, -1, 'MINUS');
INSERT INTO trit_states VALUES (2, 8, 284, -1, 'MINUS');
INSERT INTO trit_states VALUES (3, 8, 149, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (4, 8, 332, 1, 'PLUS');
INSERT INTO trit_states VALUES (5, 8, 335, 1, 'PLUS');
INSERT INTO trit_states VALUES (6, 8, 339, 1, 'PLUS');
INSERT INTO trit_states VALUES (7, 8, 175, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (8, 8, 215, -1, 'MINUS');
INSERT INTO trit_states VALUES (0, 9, 233, -1, 'MINUS');
INSERT INTO trit_states VALUES (1, 9, 287, -1, 'MINUS');
INSERT INTO trit_states VALUES (2, 9, 301, 1, 'PLUS');
INSERT INTO trit_states VALUES (3, 9, 166, 0, 'ERGODIC');
INSERT INTO trit_states VALUES (4, 9, 349, 1, 'PLUS');
INSERT INTO trit_states VALUES (5, 9, 352, 1, 'PLUS');
INSERT INTO trit_states VALUES (6, 9, 356, 1, 'PLUS');
INSERT INTO trit_states VALUES (7, 9, 192, -1, 'MINUS');
INSERT INTO trit_states VALUES (8, 9, 232, -1, 'MINUS');

-- Insert GF(3) snapshots
INSERT INTO gf3_snapshots VALUES (0, -1, 2, false);
INSERT INTO gf3_snapshots VALUES (1, -1, 2, false);
INSERT INTO gf3_snapshots VALUES (2, -3, 0, true);
INSERT INTO gf3_snapshots VALUES (3, -5, 1, false);
INSERT INTO gf3_snapshots VALUES (4, -5, 1, false);
INSERT INTO gf3_snapshots VALUES (5, -5, 1, false);
INSERT INTO gf3_snapshots VALUES (6, -3, 0, true);
INSERT INTO gf3_snapshots VALUES (7, -1, 2, false);
INSERT INTO gf3_snapshots VALUES (8, -1, 2, false);
INSERT INTO gf3_snapshots VALUES (9, 0, 0, true);

-- Insert Rezk windows
INSERT INTO rezk_windows VALUES (0, 1, 0, false, NULL);
INSERT INTO rezk_windows VALUES (1, 1, 0, false, NULL);
INSERT INTO rezk_windows VALUES (2, 0, 0, true, 0);
INSERT INTO rezk_windows VALUES (3, 0, 0, true, 0);
INSERT INTO rezk_windows VALUES (4, 0, 0, true, 0);
INSERT INTO rezk_windows VALUES (5, 0, 0, true, 0);
INSERT INTO rezk_windows VALUES (6, 0, -1, false, NULL);
INSERT INTO rezk_windows VALUES (7, 0, -1, false, NULL);
INSERT INTO rezk_windows VALUES (8, 0, -1, false, NULL);
INSERT INTO rezk_windows VALUES (9, -1, -1, true, -1);

-- ============================================================
-- USEFUL QUERIES
-- ============================================================

-- Find all balanced steps (GF(3) = 0)
-- SELECT * FROM gf3_snapshots WHERE is_balanced = true;

-- Find Rezk unification windows
-- SELECT r.*, g.is_balanced as gf3_balanced
-- FROM rezk_windows r
-- JOIN gf3_snapshots g ON r.step = g.step
-- WHERE r.is_unifiable = true;

-- Track agent trit transitions
-- SELECT
--   a.name,
--   t1.step as from_step,
--   t1.trit_name as from_trit,
--   t2.step as to_step,
--   t2.trit_name as to_trit
-- FROM trit_states t1
-- JOIN trit_states t2 ON t1.agent_id = t2.agent_id AND t2.step = t1.step + 1
-- JOIN agents a ON t1.agent_id = a.agent_id
-- WHERE t1.trit != t2.trit
-- ORDER BY t1.step, a.name;

-- Count agents per trit at each step
-- SELECT
--   step,
--   SUM(CASE WHEN trit = 1 THEN 1 ELSE 0 END) as plus_count,
--   SUM(CASE WHEN trit = 0 THEN 1 ELSE 0 END) as ergodic_count,
--   SUM(CASE WHEN trit = -1 THEN 1 ELSE 0 END) as minus_count
-- FROM trit_states
-- GROUP BY step
-- ORDER BY step;

-- Find optimal unification points (Rezk + GF3 balanced)
-- SELECT step, 'OPTIMAL' as status
-- FROM rezk_windows r
-- JOIN gf3_snapshots g USING(step)
-- WHERE r.is_unifiable = true AND g.is_balanced = true;

-- ============================================================
-- REAFFERENCE MODEL (von Holst 1950)
-- ============================================================
-- Reafference = self-caused sensory input (same trit zone)
-- Exafference = externally-caused input (different trit zone)

CREATE TABLE IF NOT EXISTS efference_copy (
  step INTEGER,
  agent_id INTEGER,
  predicted_trit INTEGER,  -- efference copy: what we expect
  actual_trit INTEGER,     -- sensory input: what we observe
  match BOOLEAN,           -- prediction matches reality
  PRIMARY KEY (step, agent_id)
);

CREATE TABLE IF NOT EXISTS reafference_perception (
  step INTEGER,
  observer_id INTEGER,
  target_id INTEGER,
  observer_trit INTEGER,
  target_trit INTEGER,
  perception VARCHAR,  -- 'REAFFERENCE' or 'EXAFFERENCE'
  PRIMARY KEY (step, observer_id, target_id)
);

-- View: Three meta-agents owning their trit zones
CREATE VIEW IF NOT EXISTS trit_zone_ownership AS
WITH zone_members AS (
  SELECT
    step,
    trit as zone,
    CASE trit WHEN 1 THEN 'PLUS-AGENT' WHEN 0 THEN 'ERGODIC-AGENT' ELSE 'MINUS-AGENT' END as meta_agent,
    list(a.name ORDER BY a.name) as owned_processes
  FROM trit_states t
  JOIN agents a USING(agent_id)
  GROUP BY step, trit
)
SELECT * FROM zone_members;

-- View: Reafference perception between observer and actor
CREATE VIEW IF NOT EXISTS observer_actor_reafference AS
SELECT
  t1.step,
  t1.trit as observer_trit,
  t2.trit as actor_trit,
  CASE WHEN t1.trit = t2.trit THEN 'REAFFERENCE' ELSE 'EXAFFERENCE' END as perception,
  CASE WHEN t1.trit = t2.trit THEN true ELSE false END as unified_consciousness
FROM trit_states t1
JOIN trit_states t2 ON t1.step = t2.step
JOIN agents a1 ON t1.agent_id = a1.agent_id
JOIN agents a2 ON t2.agent_id = a2.agent_id
WHERE a1.name = 'observer' AND a2.name = 'actor';

-- View: Third Trit - who can balance observer + actor?
-- "We are the third trit" - (a, b, -(a+b)) mod 3
CREATE VIEW IF NOT EXISTS third_trit_candidates AS
WITH third_needed AS (
  SELECT
    t1.step,
    t1.trit as obs_trit,
    t2.trit as act_trit,
    CASE
      WHEN (t1.trit + t2.trit) % 3 = 0 THEN 0
      WHEN (t1.trit + t2.trit) % 3 = 1 OR (t1.trit + t2.trit) % 3 = -2 THEN -1
      ELSE 1
    END as needed_trit
  FROM trit_states t1
  JOIN trit_states t2 ON t1.step = t2.step
  JOIN agents a1 ON t1.agent_id = a1.agent_id
  JOIN agents a2 ON t2.agent_id = a2.agent_id
  WHERE a1.name = 'observer' AND a2.name = 'actor'
)
SELECT
  t.step,
  n.obs_trit,
  n.act_trit,
  n.needed_trit,
  a.name as candidate,
  t.trit as candidate_trit,
  t.trit = n.needed_trit as is_third
FROM trit_states t
JOIN agents a ON t.agent_id = a.agent_id
JOIN third_needed n ON t.step = n.step
WHERE a.name NOT IN ('observer', 'actor');

-- View: Complete system state with reafference + GF3 + Rezk
CREATE VIEW IF NOT EXISTS system_state AS
SELECT
  r.step,
  r.observer_trit,
  r.actor_trit,
  r.perception,
  g.trit_sum,
  g.is_balanced as gf3_balanced,
  rz.is_unifiable as rezk_window,
  CASE
    WHEN r.perception = 'REAFFERENCE' AND g.is_balanced AND rz.is_unifiable
    THEN 'OPTIMAL'
    ELSE ''
  END as status
FROM observer_actor_reafference r
JOIN gf3_snapshots g USING(step)
JOIN rezk_windows rz USING(step);
