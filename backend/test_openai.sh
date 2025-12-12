#!/bin/bash

# Test OpenAI API Key Configuration
echo "🧪 Testing Clara Backend OpenAI Configuration"
echo "=============================================="
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "❌ .env file not found. Run ./setup_openai.sh first"
    exit 1
fi

# Load environment variables
source .env

# Check if API key is set
if [ -z "$OPENAI_API_KEY" ] || [ "$OPENAI_API_KEY" = "sk-your-openai-api-key-here" ]; then
    echo "❌ OpenAI API key not configured"
    echo "Run ./setup_openai.sh to configure your API key"
    exit 1
fi

echo "✅ OpenAI API key is configured"
echo ""

# Test if backend is running
echo "🔍 Checking if backend is running..."
if curl -s http://localhost:8080/health > /dev/null; then
    echo "✅ Backend is running on localhost:8080"
else
    echo "⚠️  Backend is not running. Starting it now..."
    echo ""
    echo "Starting backend in background..."
    make dev &
    BACKEND_PID=$!
    
    # Wait for backend to start
    echo "Waiting for backend to start..."
    for i in {1..30}; do
        if curl -s http://localhost:8080/health > /dev/null; then
            echo "✅ Backend started successfully"
            break
        fi
        echo -n "."
        sleep 1
    done
    
    if ! curl -s http://localhost:8080/health > /dev/null; then
        echo "❌ Failed to start backend"
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi
fi

echo ""
echo "🧪 Testing OpenAI Integration..."
echo "==============================="

# Test OpenAI API key validation
echo "Testing OpenAI API key..."
python3 -c "
import os
import requests
import json

api_key = os.getenv('OPENAI_API_KEY')
if not api_key:
    print('❌ OPENAI_API_KEY not found in environment')
    exit(1)

# Test with a simple request
headers = {
    'Authorization': f'Bearer {api_key}',
    'Content-Type': 'application/json'
}

data = {
    'model': 'gpt-3.5-turbo',
    'messages': [{'role': 'user', 'content': 'Hello'}],
    'max_tokens': 5
}

try:
    response = requests.post(
        'https://api.openai.com/v1/chat/completions',
        headers=headers,
        json=data,
        timeout=10
    )
    
    if response.status_code == 200:
        print('✅ OpenAI API key is valid')
        result = response.json()
        print(f'   Response: {result[\"choices\"][0][\"message\"][\"content\"]}')
    else:
        print(f'❌ OpenAI API error: {response.status_code} - {response.text}')
        exit(1)
        
except Exception as e:
    print(f'❌ Error testing OpenAI API: {e}')
    exit(1)
"

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 All tests passed!"
    echo ""
    echo "Next steps:"
    echo "1. Run the Android app"
    echo "2. Navigate to Voice Assistant screen"
    echo "3. Test voice chat with Clara"
    echo ""
    echo "Backend is running on: http://localhost:8080"
    echo "Health check: curl http://localhost:8080/health"
else
    echo ""
    echo "❌ Tests failed. Check your OpenAI API key configuration."
    exit 1
fi