#!/usr/bin/env python3
"""Test MLX server connectivity with OpenAI-compatible API"""
import sys
import json

def test_mlx(base_url="http://localhost:8080/v1"):
    try:
        import urllib.request
        
        # Test /v1/models endpoint
        req = urllib.request.Request(f"{base_url}/models")
        with urllib.request.urlopen(req, timeout=5) as resp:
            models = json.loads(resp.read())
            print(f"✓ Server reachable at {base_url}")
            print(f"  Models: {[m.get('id', 'unknown') for m in models.get('data', [])]}")
        
        # Test chat completion - use actual model name, not "default"
        model_id = models.get('data', [{}])[0].get('id', 'default')
        chat_req = {
            "model": model_id,
            "messages": [{"role": "user", "content": "Say 'MLX works!' in exactly 3 words"}],
            "max_tokens": 20
        }
        req = urllib.request.Request(
            f"{base_url}/chat/completions",
            data=json.dumps(chat_req).encode(),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read())
            content = result["choices"][0]["message"]["content"]
            print(f"✓ Chat completion works")
            print(f"  Response: {content[:100]}")
        
        print("\n✅ MLX server ready for asi_agent!")
        print(f"   export OPENAI_BASE_URL={base_url}")
        return 0
        
    except urllib.error.URLError as e:
        print(f"✗ Cannot connect to {base_url}")
        print(f"  Error: {e}")
        print("\nStart MLX server first:")
        print("  just mlx-server")
        return 1
    except Exception as e:
        print(f"✗ Error: {e}")
        return 1

if __name__ == "__main__":
    url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/v1"
    sys.exit(test_mlx(url))
