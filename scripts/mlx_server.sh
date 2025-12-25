#!/bin/bash
# Start MLX LLM server with OpenAI-compatible API
# Usage: ./scripts/mlx_server.sh [model]

MODEL="${1:-mlx-community/Qwen2.5-7B-Instruct-4bit}"
PORT="${MLX_PORT:-8080}"

echo "Starting MLX server with $MODEL on port $PORT"
echo "OpenAI-compatible endpoint: http://localhost:$PORT/v1"
echo ""
echo "To use with asi_agent, set:"
echo "  export OPENAI_API_KEY=mlx-local"
echo "  export OPENAI_BASE_URL=http://localhost:$PORT/v1"
echo ""

uvx --from mlx-lm mlx_lm.server \
  --model "$MODEL" \
  --host 0.0.0.0 \
  --port "$PORT"
