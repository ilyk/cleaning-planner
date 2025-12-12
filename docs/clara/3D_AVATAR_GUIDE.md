# 3D Avatar System - Developer Guide

## Overview

The Cleaning Planner app features a sophisticated 3D avatar system powered by SceneView/Filament, with LLM-generated content support via OpenAI (GPT-4o/gpt-4o-mini).

## Architecture

### Components

1. **AvatarProvider Interface** (`feature/clara/avatar/`)
   - Abstract interface for 3D rendering backends
   - `SceneViewAvatarProvider`: SceneView/Filament implementation

2. **AvatarRepository** (`feature/clara/repository/`)
   - Manages avatar assets (import, cache, delete)
   - GLB validation and optimization
   - File/URL import support

3. **VisemeEngine** (`feature/clara/lipsync/`)
   - Lip-sync via viseme mapping
   - Amplitude-based fallback for models without morph targets

4. **Performance Monitoring** (`feature/clara/ui/diagnostics/`)
   - Real-time FPS, frame time, jank tracking
   - 10-second performance probe
   - Texture memory monitoring

5. **Avatar Generation** (`feature/clara/generation/`)
   - Provider abstraction for future 3D generation APIs
   - GPT-4o prompt crafting service
   - Meshy.ai integration (ready for API keys)

## Bundled Avatar

### Clara (Default)
- **File**: `app/src/main/assets/avatars/clara_default.glb`
- **Size**: 924KB
- **Triangles**: ~80,000 (estimated)
- **Features**: Idle animation, blink, PBR materials
- **License**: See `app/src/main/assets/avatars/LICENSE.txt`

## User Features

### Avatar Settings
- **Appearance Selection**: Choose from imported/bundled avatars
- **Name & Pronunciation**: Custom display name with phonetic/IPA/SSML support
- **Voice Styles**: Warm, Bright, Calm (affects TTS pitch/rate)
- **Visibility & Audio**: Show avatar, mute voice, show subtitles
- **Import**: File picker or HTTPS URL with license tracking

### Lip-Sync
- Uses viseme morph targets when available
- Falls back to amplitude-based jaw movement
- Subtitles always visible (WCAG compliant)

### Performance
- Target: ≥60 FPS on mid-range devices
- Graceful degradation (LOD system ready)
- Developer diagnostics screen (hidden by default)

## LLM Integration

### GPT-4o (Primary) / gpt-4o-mini (Fallback)
- **System Prompt**: Warm, concise, emotionally intelligent tone
- **Parameters**: temp=0.4, top_p=0.9, max_tokens=150
- **Use Cases**:
  - Welcome screen hand-off lines
  - Conversational intake responses
  - 3D avatar prompt crafting (future)

### Fallback Handling
- Silent fallback to gpt-4o-mini on GPT-4o errors
- Static responses if API key missing/invalid
- Non-blocking errors (soft banners)

## Database Schema

### avatar_assets Table
```sql
CREATE TABLE avatar_assets (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    default_name TEXT NOT NULL,
    glb_path TEXT NOT NULL,
    thumbnail_path TEXT,
    source_type TEXT NOT NULL, -- bundled/imported_file/imported_url
    source_location TEXT,
    license_note TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    file_size_bytes INTEGER NOT NULL,
    triangle_count INTEGER NOT NULL,
    has_visemes INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    version INTEGER DEFAULT 1,
    is_active INTEGER DEFAULT 1
);
```

## DataStore (Encrypted)

### Avatar3DPrefs
- `appearanceId`: Selected avatar ID
- `displayName`: Custom name
- `pronunciationMode`: NONE/PHONETIC/IPA/SSML
- `pronunciationValue`: Pronunciation string
- `voiceId`: warm/bright/calm
- `showAvatar`: Boolean
- `muteVoice`: Boolean
- `alwaysShowSubtitles`: Boolean (default: true)

## Accessibility

- TalkBack labels on all controls
- Live regions for subtitles
- High-contrast subtitle backgrounds
- Reduce motion support
- Keyboard navigation

## Performance Targets

| Metric | Target | Acceptable |
|--------|--------|------------|
| FPS | ≥60 | ≥55 |
| Frame Time | ≤16.67ms | ≤18ms |
| Jank | <5% | <10% |
| GLB Size | ≤10MB | ≤15MB |
| Triangles | ≤80k | ≤100k |
| Texture Memory | <50MB | <75MB |

## Future Enhancements

1. **LLM-Generated Avatars**
   - Meshy.ai integration (API ready)
   - Luma, Kaedim support
   - GPT-4o prompt crafting live

2. **Animation**
   - Emotional expressions
   - Gesture library
   - Reaction animations

3. **Ready Player Me**
   - Avatar SDK integration
   - User-customizable avatars

## Troubleshooting

### Avatar Won't Load
1. Check file is valid GLB (magic bytes: `glTF`)
2. Verify file size ≤10MB
3. Check device GL ES version ≥3.0
4. Review diagnostics screen

### Poor Performance
1. Run diagnostics probe
2. Check triangle count
3. Enable LOD/degradation
4. Reduce texture resolution

### Lip-Sync Issues
1. Verify viseme morph targets present
2. Check amplitude fallback enabled
3. Review TTS timing
4. Test with different voice styles

## API Keys Required

### OpenAI
- **Endpoint**: https://api.openai.com/v1/chat/completions
- **Models**: gpt-4o (primary), gpt-4o-mini (fallback)
- **Configured In**: Settings → AI Assistant

### Meshy.ai (Future)
- **Endpoint**: https://api.meshy.ai/v2/text-to-3d
- **Feature**: Avatar generation
- **Status**: Interface ready, disabled until configured

## License

All bundled avatars are documented in `/app/src/main/assets/avatars/LICENSE.txt`.

Imported avatars require user-provided license notes stored in database.

---

**Version**: 1.0
**Last Updated**: 2025-10-18

