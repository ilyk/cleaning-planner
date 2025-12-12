#!/usr/bin/env python3
"""
Generate JWT tokens for testing Clara backend

Usage:
    python3 examples/generate_jwt.py

Requirements:
    pip install pyjwt
"""

import jwt
import time
import sys

def generate_token(secret="your-secret-key-here", user_id="user-123", home_id="home-789"):
    """Generate a JWT token with Clara claims"""
    
    session_id = f"sess-{int(time.time())}"
    
    payload = {
        "sub": user_id,
        "sid": session_id,
        "home_id": home_id,
        "exp": int(time.time()) + 3600  # 1 hour expiration
    }
    
    token = jwt.encode(payload, secret, algorithm="HS256")
    
    return token, payload

if __name__ == "__main__":
    if len(sys.argv) > 1:
        secret = sys.argv[1]
    else:
        secret = "your-secret-key-here"
        print("Using default secret. Pass custom secret as first argument.")
        print()
    
    token, payload = generate_token(secret)
    
    print("JWT Token Generated")
    print("=" * 80)
    print()
    print("Token:")
    print(token)
    print()
    print("Payload:")
    for key, value in payload.items():
        print(f"  {key}: {value}")
    print()
    print("To use with curl:")
    print(f'  curl -H "Authorization: Bearer {token}" http://localhost:8080/v1/clara/session')
    print()

