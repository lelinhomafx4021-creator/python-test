import requests
import os
from dotenv import load_dotenv

load_dotenv()

api_key = os.getenv("DASH_API_KEY")
api_url = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"

payload = {
    "model": "text-embedding-v3",
    "input": ["你好"],
}
headers = {
    "Authorization": f"Bearer {api_key}",
    "Content-Type": "application/json",
}

try:
    print(f"Testing Aliyun Embedding API with key: {api_key[:10]}...")
    resp = requests.post(api_url, json=payload, headers=headers, timeout=10)
    print(f"Status: {resp.status_code}")
    if resp.status_code == 200:
        print("Success! Embedding received.")
        print(f"Vector sample: {resp.json()['data'][0]['embedding'][:5]}")
    else:
        print(f"Error: {resp.text}")
except Exception as e:
    print(f"Failed: {e}")
