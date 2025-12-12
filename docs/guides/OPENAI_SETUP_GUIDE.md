# OpenAI API Key Setup Guide

## Architecture Overview

The CleanFlow app uses a **backend-only** approach for OpenAI integration:

```
Android App → Clara Backend → OpenAI API
```

- ✅ **Android App**: No direct OpenAI access
- ✅ **Clara Backend**: Handles all OpenAI communication
- ✅ **Security**: API keys stored only on backend

## Quick Setup

### 1. Get OpenAI API Key

1. Go to [platform.openai.com](https://platform.openai.com)
2. Sign in or create an account
3. Navigate to **API Keys**
4. Click **Create new secret key**
5. Copy the key (starts with `sk-`)

### 2. Configure Backend

```bash
cd backend

# Run the setup script
./setup_openai.sh
```

Or manually:

```bash
# Copy environment template
cp .env.example .env

# Edit .env file
nano .env

# Set your API key
OPENAI_API_KEY=sk-your-actual-api-key-here

# Start the backend
make dev
```

### 3. Test the Setup

```bash
# Check if backend is running
curl http://localhost:8080/health

# Should return: {"status":"ok","version":"0.1.0"}
```

## Backend Configuration

The backend uses these environment variables:

```bash
# Required for Clara AI features
OPENAI_API_KEY=sk-your-key-here

# Optional: JWT secret for authentication
JWT_SECRET=your-secret-key

# Optional: Database (uses memory by default)
DATABASE_URL=memory://

# Optional: Redis (uses localhost by default)
REDIS_URL=redis://localhost:6379
```

## Android App Configuration

The Android app is already configured to connect to the backend:

- **Base URL**: `http://localhost:8080`
- **Protocol**: Clara Streaming Protocol (WebSocket)
- **Authentication**: JWT tokens

## Testing Clara Voice Features

1. **Start Backend**:
   ```bash
   cd backend
   make dev
   ```

2. **Run Android App**:
   - Open in Android Studio
   - Run on device/emulator
   - Navigate to Voice Assistant screen

3. **Test Voice Chat**:
   - Tap the microphone button
   - Speak your message
   - Clara will respond via the backend

## Troubleshooting

### Backend Issues

**"OpenAI API key not configured"**
- Check `.env` file has `OPENAI_API_KEY=sk-...`
- Restart backend after changing `.env`

**"Connection refused"**
- Make sure backend is running: `make dev`
- Check port 8080 is available

**"Invalid API key"**
- Verify key starts with `sk-`
- Check key is active on OpenAI platform
- Try creating a new key

### Android App Issues

**"Cannot connect to backend"**
- Make sure backend is running on `localhost:8080`
- For physical device, use your computer's IP instead of `localhost`
- Check network connectivity

**"Voice not working"**
- Grant microphone permission
- Check backend logs for errors
- Verify OpenAI API key is working

## Security Notes

- ✅ API keys are stored only on the backend
- ✅ Android app never sees the OpenAI API key
- ✅ All communication is encrypted (HTTPS/WSS in production)
- ✅ JWT tokens provide secure authentication

## Production Deployment

For production, update these settings:

1. **Backend URL**: Change from `localhost:8080` to your server
2. **HTTPS**: Use `https://` instead of `http://`
3. **JWT Secret**: Use a strong, random secret
4. **Database**: Use real PostgreSQL instead of memory
5. **Redis**: Use production Redis instance

## Cost Management

OpenAI API usage is tracked in the backend logs:

```bash
# Check usage in backend logs
tail -f backend/logs/clara.log | grep "tokens"
```

Monitor your usage at [platform.openai.com/usage](https://platform.openai.com/usage)