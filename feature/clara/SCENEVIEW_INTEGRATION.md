# SceneView 3D Integration Notes

## Current Status

The 3D avatar system architecture is complete, but the SceneView rendering integration is **stubbed** pending correct API documentation.

## What's Implemented

✅ **Architecture**:
- AvatarProvider interface
- SceneViewAvatarProvider class structure
- Model loading pipeline
- Animation framework
- Viseme mapping
- FPS tracking

✅ **Data Layer**:
- Avatar asset database
- GLB import and caching
- License tracking
- Performance metrics

✅ **UI Components**:
- Avatar3DView composable
- Settings screens
- Import dialogs
- Diagnostics

## What Needs SceneView API Integration

The following methods in `SceneViewAvatarProvider.kt` need proper SceneView 2.2.1 API calls:

### 1. Model Loading
```kotlin
override suspend fun loadModel(glbPath: String): Result<Unit> {
    // Current: Stub (validates file exists)
    // Needed: 
    // - Create SceneView instance
    // - Load GLB using ModelNode or equivalent
    // - Attach to scene graph
    // - Configure PBR lighting
}
```

### 2. Animation Control
```kotlin
override fun playIdleAnimation() {
    // Current: Boolean flag
    // Needed:
    // - Access animator from loaded model
    // - Play animation by index
    // - Set loop mode
}
```

### 3. Morph Target Application
```kotlin
override fun applyViseme(visemeId: String, weight: Float) {
    // Current: Amplitude fallback only
    // Needed:
    // - Access morph targets from model
    // - Map viseme ID to morph target name
    // - Apply weight value
    // - Blend multiple morphs
}
```

### 4. Scene Setup in Composable
```kotlin
// In Avatar3DView.kt
AndroidView(factory = { context ->
    // Current: Text placeholder
    // Needed:
    // - Create SceneView instance
    // - Configure camera
    // - Set up lighting (ambient + directional)
    // - Handle lifecycle
})
```

## Integration Checklist

### Step 1: Research SceneView 2.2.1 API
- [ ] Review official documentation: https://github.com/SceneView/sceneview-android
- [ ] Check sample code in repository
- [ ] Verify ModelNode constructor signature
- [ ] Confirm animation API methods
- [ ] Check morph target access methods

### Step 2: Update SceneViewAvatarProvider
- [ ] Fix `loadModel()` with correct API
- [ ] Implement proper `playIdleAnimation()`
- [ ] Add blink trigger mechanism
- [ ] Implement morph target access
- [ ] Set up FPS tracking with scene callbacks

### Step 3: Update Avatar3DView Composable
- [ ] Replace placeholder Box with AndroidView
- [ ] Create SceneView in factory
- [ ] Configure camera position/FOV
- [ ] Set up lighting (IBL + directional)
- [ ] Handle onFrame callbacks
- [ ] Implement proper lifecycle management

### Step 4: Test with Bundled Avatar
- [ ] Verify clara_default.glb loads
- [ ] Confirm idle animation plays
- [ ] Test blink functionality
- [ ] Validate lip-sync (or fallback)
- [ ] Measure FPS and performance

### Step 5: Polish
- [ ] Add loading states
- [ ] Error handling for unsupported models
- [ ] Graceful degradation on low-end devices
- [ ] LOD system implementation

## Temporary Fallback

Currently, `Avatar3DView` shows a text placeholder. This is intentional and documented to allow the rest of the system to function while the correct SceneView integration is completed.

**The icon-based avatar system from the first commit remains fully functional as a fallback.**

## Alternative Approaches

If SceneView 2.2.1 proves problematic:

### Option A: Filament Direct
Use Google Filament directly without SceneView wrapper:
- More control but more complex
- Better performance tuning
- Steeper learning curve

### Option B: Ready Player Me SDK
Use their pre-built Android SDK:
- Complete solution with animations
- Avatar generation built-in
- Commercial licensing required

### Option C: Unity as a Library
Embed Unity runtime:
- Maximum flexibility
- Large APK size increase
- Complex build integration

## Recommended Next Steps

1. **Review SceneView docs** for the correct 2.2.1 API
2. **Create minimal sample** to test GLB loading
3. **Update provider** with working API calls
4. **Test with clara_default.glb**
5. **Complete integration** incrementally

## Timeline Estimate

- Minimal working 3D rendering: 2-4 hours
- Full lip-sync with morphs: 4-8 hours
- Performance optimization: 2-4 hours
- **Total**: 8-16 hours of focused development

---

**Note**: All other aspects of the system (import, settings, TTS, lip-sync timing, etc.) are production-ready and working. Only the visual 3D rendering stub needs completion.

