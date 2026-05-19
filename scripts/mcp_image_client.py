#!/usr/bin/env python3
"""
MiniMax MCP 图片理解客户端
通过 minimax-coding-plan-mcp 的 understand_image 工具理解图片
"""
import subprocess
import json
import sys
import os

def call_mcp_understand_image(image_url, prompt="请描述这张图片"):
    """调用 MCP understand_image 工具"""
    # 启动 MCP Server 进程
    env = os.environ.copy()
    env["MINIMAX_API_KEY"] = env.get("MINIMAX_API_KEY", "")
    env["MINIMAX_API_HOST"] = "https://api.minimaxi.com"
    
    process = subprocess.Popen(
        ["uvx", "minimax-coding-plan-mcp", "-y"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        text=True
    )
    
    # 发送 JSON-RPC 请求
    request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "understand_image",
            "arguments": {
                "prompt": prompt,
                "image_url": image_url
            }
        }
    }
    
    process.stdin.write(json.dumps(request) + "\n")
    process.stdin.flush()
    
    # 读取响应
    response = process.stdout.readline()
    process.stdin.close()
    process.stdout.close()
    process.terminate()
    
    if response:
        resp_data = json.loads(response)
        if "result" in resp_data:
            result = resp_data["result"]
            if isinstance(result, dict) and "content" in result:
                return result["content"]
            elif isinstance(result, str):
                return result
    return None

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python mcp_image_client.py <image_url> [prompt]")
        sys.exit(1)
    
    image_url = sys.argv[1]
    prompt = sys.argv[2] if len(sys.argv) > 2 else "请描述这张图片"
    
    result = call_mcp_understand_image(image_url, prompt)
    if result:
        print(result)
    else:
        print("图片理解失败", file=sys.stderr)
        sys.exit(1)