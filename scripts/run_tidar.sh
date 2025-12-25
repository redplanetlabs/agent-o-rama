#!/bin/bash
set -e
echo "=== TIDAR: Tree-structured Iterative Decomposition and Aggregation with Rollup ==="
echo "7 roots × 3 children = 21 leaf agents"
/tmp/mlx-env/bin/python /Users/alice/agent-o-rama/agent-o-rama/scripts/tidar_mlx_runner.py
echo "=== TIDAR COMPLETE ==="
