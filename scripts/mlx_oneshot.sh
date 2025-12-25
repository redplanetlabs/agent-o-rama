#!/bin/bash
# MLX one-shot server - run local LLM with OpenAI-compatible API
# Usage: ./scripts/mlx_oneshot.sh [small|default|MODEL_NAME]

set -e

case "${1:-default}" in
  small|test)
    MODEL="mlx-community/Qwen2.5-0.5B-Instruct-4bit"
    echo "Using small model for testing (278MB)"
    ;;
  default)
    MODEL="mlx-community/Qwen2.5-7B-Instruct-4bit"
    echo "Using default model (4GB)"
    ;;
  *)
    MODEL="$1"
    echo "Using custom model: $MODEL"
    ;;
esac

PORT="${MLX_PORT:-8080}"

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  MLX LLM Server (OpenAI-compatible API)    ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "Model: $MODEL"
echo "Endpoint: http://localhost:$PORT/v1"
echo ""
echo "To use with asi_agent:"
echo "  export OPENAI_BASE_URL=http://localhost:$PORT/v1"
echo "  lein run -m com.rpl.agent.asi-agent \"your topic\""
echo ""
echo "Starting server..."
echo ""

uvx --from mlx-lm mlx_lm.server \
  --model "$MODEL" \
  --host 0.0.0.0 \
  --port "$PORT" \
  --use-default-chat-template
