package com.ilyk.cleaningplanner.feature.clara.ui.components

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
        avatarProvider.attachToSceneView(sceneView)
        val result = avatarProvider.loadModel(glbPath)
        if (result.isSuccess) {
            avatarProvider.playIdleAnimation()
            onLoaded?.invoke()
        } else {
            onError?.invoke(result.exceptionOrNull()?.message ?: "Failed to load model")
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

