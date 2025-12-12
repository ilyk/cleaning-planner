#!/bin/bash

# Clara Backend OpenAI Setup Script
echo "🔧 Setting up Clara Backend with OpenAI API Key"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "📋 Creating .env file from template..."
    cp .env.example .env
    echo "✅ Created .env file"
else
    echo "📋 .env file already exists"
fi

echo ""
echo "🔑 OpenAI API Key Configuration"
echo "================================"
echo ""
echo "To use Clara's AI features, you need to:"
echo "1. Go to https://platform.openai.com"
echo "2. Sign in or create an account"
echo "3. Navigate to API Keys"
echo "4. Create a new secret key"
echo "5. Copy the key (starts with 'sk-')"
echo ""

# Check if OPENAI_API_KEY is already set
if grep -q "OPENAI_API_KEY=sk-" .env; then
    echo "✅ OpenAI API key is already configured in .env"
    echo ""
    echo "Current configuration:"
    grep "OPENAI_API_KEY=" .env | sed 's/OPENAI_API_KEY=sk-.*/OPENAI_API_KEY=sk-***hidden***/'
else
    echo "⚠️  OpenAI API key not configured yet"
    echo ""
    read -p "Enter your OpenAI API key (sk-...): " api_key
    
    if [[ $api_key == sk-* ]]; then
        # Update the .env file
        sed -i "s/OPENAI_API_KEY=.*/OPENAI_API_KEY=$api_key/" .env
        echo "✅ OpenAI API key configured successfully"
    else
        echo "❌ Invalid API key format. Please make sure it starts with 'sk-'"
        exit 1
    fi
fi

echo ""
echo "🚀 Starting Clara Backend..."
echo "============================="
echo ""

# Start the backend with the configured API key
make dev