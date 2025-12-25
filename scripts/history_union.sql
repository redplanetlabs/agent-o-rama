-- HISTORY UNION: Query all agent history via DuckDB
-- Run with: duckdb < scripts/history_union.sql

-- Create unified view across all agents
CREATE OR REPLACE VIEW unified_history AS
WITH 
amp_history AS (
    SELECT 
        'amp' as agent,
        filename as thread_id,
        file_modified_time as mtime
    FROM glob('/Users/alice/.amp/file-changes/*')
),
claude_history AS (
    SELECT 
        'claude' as agent,
        json_extract_string(line, '$.id') as thread_id,
        json_extract_string(line, '$.timestamp') as mtime
    FROM read_csv('/Users/alice/.claude/history.jsonl', 
                  columns={'line': 'VARCHAR'}, 
                  header=false,
                  filename=false)
    WHERE line IS NOT NULL AND line != ''
    LIMIT 1000
),
codex_history AS (
    SELECT 
        'codex' as agent,
        json_extract_string(line, '$.id') as thread_id,
        json_extract_string(line, '$.timestamp') as mtime
    FROM read_csv('/Users/alice/.codex/history.jsonl',
                  columns={'line': 'VARCHAR'},
                  header=false,
                  filename=false)
    WHERE line IS NOT NULL AND line != ''
    LIMIT 1000
)
SELECT * FROM amp_history
UNION ALL
SELECT * FROM claude_history  
UNION ALL
SELECT * FROM codex_history;

-- Count by agent
SELECT agent, COUNT(*) as count 
FROM unified_history 
GROUP BY agent
ORDER BY count DESC;

-- GF(3) verification
SELECT 
    agent,
    COUNT(*) as count,
    CASE agent 
        WHEN 'amp' THEN 1 
        WHEN 'claude' THEN 0 
        WHEN 'codex' THEN -1 
    END as trit,
    COUNT(*) * CASE agent 
        WHEN 'amp' THEN 1 
        WHEN 'claude' THEN 0 
        WHEN 'codex' THEN -1 
    END as contribution
FROM unified_history
GROUP BY agent;

-- Total and mod 3
SELECT 
    SUM(contribution) as total_sum,
    SUM(contribution) % 3 as mod3,
    CASE WHEN SUM(contribution) % 3 = 0 THEN 'CONSERVED' ELSE 'IMBALANCED' END as gf3_status
FROM (
    SELECT COUNT(*) * CASE agent 
        WHEN 'amp' THEN 1 
        WHEN 'claude' THEN 0 
        WHEN 'codex' THEN -1 
    END as contribution
    FROM unified_history
    GROUP BY agent
);
