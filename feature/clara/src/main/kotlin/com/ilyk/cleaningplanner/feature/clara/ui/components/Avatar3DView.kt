package com.ilyk.cleaningplanner.feature.clara.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ilyk.cleaningplanner.feature.clara.avatar.SceneViewAvatarProvider
import io.github.sceneview.SceneView

/**
 * Composable that displays a 3D avatar using SceneView.
 * Loads and renders GLB models with animations.
 */
@Composable
fun Avatar3DView(
    glbPath: String,
    avatarProvider: SceneViewAvatarProvider,
    modifier: Modifier = Modifier,
    onLoaded: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val sceneView = remember(context) {
        SceneView(context)
    }
    
    LaunchedEffect(glbPath, sceneView) {
        Log.d("Avatar3DView", "Loading GLB from: $glbPath")
        avatarProvider.attachToSceneView(sceneView)
        val result = avatarProvider.loadModel(glbPath)
        if (result.isSuccess) {
            Log.d("Avatar3DView", "GLB loaded successfully, playing idle animation")
            avatarProvider.playIdleAnimation()
            onLoaded?.invoke()
        } else {
            val error = result.exceptionOrNull()?.message ?: "Failed to load model"
            Log.e("Avatar3DView", "Failed to load GLB: $error")
            onError?.invoke(error)
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_DESTROY -> avatarProvider.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            avatarProvider.release()
        }
    }
    
    AndroidView(
        factory = { sceneView },
        modifier = modifier
    )
}

