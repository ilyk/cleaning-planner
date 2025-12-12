#!/bin/bash
# Smoke test for Clara backend - no app required

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
JWT_SECRET="${JWT_SECRET:-dev_jwt_secret}"

echo "🧪 Clara Backend Smoke Test"
echo "=========================="
echo ""

# Generate JWT token
echo "1️⃣  Generating JWT token..."
TOKEN=$(python3 -c "
from datetime import datetime, timedelta
import jwt
import sys
secret = '$JWT_SECRET'
claims = {
    'sub': 'test_user',
    'sid': 'test_session',
    'home_id': 'test_home',
    'exp': int((datetime.now() + timedelta(hours=1)).timestamp())
}
token = jwt.encode(claims, secret, algorithm='HS256')
print(token)
" 2>/dev/null || echo "jwt-token-placeholder")

if [ "$TOKEN" = "jwt-token-placeholder" ]; then
    echo "⚠️  JWT generation failed, using placeholder"
    echo "   Install: pip install PyJWT"
fi

echo "✅ Token: ${TOKEN:0:20}..."
echo ""

# 1) Create session
echo "2️⃣  Creating session..."
SESSION_RESP=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -X POST "$BASE_URL/v1/clara/session")

HTTP_CODE=$(echo "$SESSION_RESP" | tail -n1)
SESSION_BODY=$(echo "$SESSION_RESP" | head -n-1)

if [ "$HTTP_CODE" != "200" ]; then
    echo "❌ Session creation failed: HTTP $HTTP_CODE"
    echo "$SESSION_BODY"
    exit 1
fi

SESSION_ID=$(echo "$SESSION_BODY" | grep -o '"sessionId":"[^"]*"' | cut -d'"' -f4 || echo "")
echo "✅ Session created: $SESSION_ID"
echo ""

# 2) Start turn
echo "3️⃣  Starting turn..."
TURN_RESP=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -X POST "$BASE_URL/v1/clara/session/turn" \
    -d '{"mode":"focus"}')

HTTP_CODE=$(echo "$TURN_RESP" | tail -n1)
TURN_BODY=$(echo "$TURN_RESP" | head -n-1)

if [ "$HTTP_CODE" != "200" ]; then
    echo "❌ Turn creation failed: HTTP $HTTP_CODE"
    echo "$TURN_BODY"
    exit 1
fi

TURN_ID=$(echo "$TURN_BODY" | grep -o '"turnId":"[^"]*"' | cut -d'"' -f4 || echo "")
echo "✅ Turn created: $TURN_ID"
echo ""

# 3) Health check
echo "4️⃣  Health check..."
HEALTH=$(curl -s "$BASE_URL/health")
if echo "$HEALTH" | grep -q "ok\|status"; then
    echo "✅ Health check passed"
else
    echo "⚠️  Health check response: $HEALTH"
fi
echo ""

# 4) Metrics endpoint
echo "5️⃣  Metrics endpoint..."
METRICS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/metrics")
if [ "$METRICS_CODE" = "200" ]; then
    echo "✅ Metrics endpoint accessible"
else
    echo "⚠️  Metrics endpoint: HTTP $METRICS_CODE"
fi
echo ""

echo "✅ Smoke test complete!"
echo ""
echo "📝 To test WebSocket connection:"
echo "   wscat -c \"ws://localhost:8080/v1/clara/stream?sessionId=$SESSION_ID&turnId=$TURN_ID\" \\"
echo "         -H \"Authorization: Bearer $TOKEN\""
echo ""
echo "   Then send:"
echo "   {\"type\":\"turn.start\",\"sessionId\":\"$SESSION_ID\",\"turnId\":\"$TURN_ID\"}"
echo "   {\"type\":\"input.audio.delta\",\"seq\":1,\"format\":\"opus@24000\",\"data\":\"$(echo -n 'test' | base64)\"}"
echo "   {\"type\":\"input.audio.commit\",\"seq\":1}"

