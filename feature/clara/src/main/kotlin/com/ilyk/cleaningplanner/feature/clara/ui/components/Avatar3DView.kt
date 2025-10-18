package com.ilyk.cleaningplanner.feature.clara.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ilyk.cleaningplanner.feature.clara.avatar.SceneViewAvatarProvider
import io.github.sceneview.SceneView

/**
 * Composable that displays a 3D avatar using SceneView.
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
    
    val sceneView = remember {
        SceneView(context).apply {
            // Configure scene
            isOpaque = false
        }
    }
    
    LaunchedEffect(glbPath) {
        avatarProvider.attachToSceneView(sceneView)
        
        val result = avatarProvider.loadModel(glbPath)
        if (result.isSuccess) {
            avatarProvider.playIdleAnimation()
            onLoaded?.invoke()
        } else {
            onError?.invoke(result.exceptionOrNull()?.message ?: "Failed to load model")
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            avatarProvider.release()
        }
    }
    
    AndroidView(
        factory = { sceneView },
        modifier = modifier.fillMaxSize()
    )
}

