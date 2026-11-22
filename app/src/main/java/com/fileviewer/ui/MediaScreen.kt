package com.fileviewer.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fileviewer.detect.Kind
import com.fileviewer.detect.OpenedFile

/** Pemutar video & audio. ExoPlayer juga mengenali container dari isi file, bukan ekstensi. */
@OptIn(UnstableApi::class)
@Composable
fun MediaScreen(file: OpenedFile) {
    val context = LocalContext.current
    val isAudio = file.type.kind == Kind.AUDIO
    var error by remember(file.uri) { mutableStateOf<PlaybackException?>(null) }

    val player = remember(file.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = e
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Jeda otomatis saat aplikasi ke background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val e = error
    if (e != null) {
        ViewerError(file, "Tidak bisa diputar (${e.errorCodeName}).")
        return
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                if (isAudio) {
                    controllerShowTimeoutMs = 0
                    controllerHideOnTouch = false
                }
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}
