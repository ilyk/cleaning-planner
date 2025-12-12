#!/bin/bash
# Test Clara backend session flow

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
JWT_SECRET="${JWT_SECRET:-your-secret-key-here}"

echo "Clara Backend Test Script"
echo "========================="
echo ""

# Generate JWT token
echo "1. Generating JWT token..."
TOKEN=$(python3 examples/generate_jwt.py "$JWT_SECRET" | grep "^eyJ" | head -1)

if [ -z "$TOKEN" ]; then
    echo "Error: Failed to generate token"
    exit 1
fi

echo "Token: ${TOKEN:0:50}..."
echo ""

# Health check
echo "2. Health check..."
curl -s "$BASE_URL/health" | jq .
echo ""

# Create session
echo "3. Creating session..."
SESSION_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/clara/session" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")

echo "$SESSION_RESPONSE" | jq .

SESSION_ID=$(echo "$SESSION_RESPONSE" | jq -r .session_id)

if [ -z "$SESSION_ID" ] || [ "$SESSION_ID" == "null" ]; then
    echo "Error: Failed to create session"
    exit 1
fi

echo ""

# Start turn
echo "4. Starting turn..."
TURN_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/clara/session/turn" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"session_id\":\"$SESSION_ID\"}")

echo "$TURN_RESPONSE" | jq .

TURN_ID=$(echo "$TURN_RESPONSE" | jq -r .turn_id)

if [ -z "$TURN_ID" ] || [ "$TURN_ID" == "null" ]; then
    echo "Error: Failed to start turn"
    exit 1
fi

echo ""

# Check metrics
echo "5. Checking metrics..."
curl -s "$BASE_URL/metrics" | grep "clara_" | head -5
echo "..."
echo ""

echo "Success! Session created: $SESSION_ID, Turn: $TURN_ID"
echo ""
echo "To connect via WebSocket:"
echo "  wscat -c \"ws://localhost:8080/v1/clara/stream?sessionId=$SESSION_ID&turnId=$TURN_ID\" \\"
echo "    -H \"Authorization: Bearer $TOKEN\""

