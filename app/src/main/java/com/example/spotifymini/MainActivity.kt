package com.example.spotifymini

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.palette.graphics.Palette
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Image as SpotifyImage
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.delay

private val DEFAULT_BACKGROUND = Color(0xFF1A1A1A)

class MainActivity : ComponentActivity() {

    private var spotifyAppRemote: SpotifyAppRemote? = null

    // ⚠️ Replace with the Client ID from your app in the Spotify Developer Dashboard
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "spotifyminiplayer://callback"

    private val trackState = mutableStateOf<Track?>(null)
    private val isPaused = mutableStateOf(true)
    private val albumArt = mutableStateOf<Bitmap?>(null)
    private val progressMs = mutableStateOf(0L)
    private val durationMs = mutableStateOf(1L)
    private val connectionStatus = mutableStateOf("Connecting...")
    private val backgroundColor = mutableStateOf(DEFAULT_BACKGROUND)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars and hide them for an immersive, edge-to-edge look.
        // The bars can still be revealed temporarily with a swipe from the screen edge.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        if (clientId.isBlank()) {
            Toast.makeText(
                this,
                "Missing Spotify Client ID. Set spotify.client.id in local.properties (see README).",
                Toast.LENGTH_LONG
            ).show()
        }

        setContent {
            MaterialTheme {
                PlayerScreen(
                    track = trackState.value,
                    albumArt = albumArt.value,
                    isPaused = isPaused.value,
                    progressMs = progressMs.value,
                    durationMs = durationMs.value,
                    connectionStatus = connectionStatus.value,
                    backgroundColor = backgroundColor.value,
                    onPlayPause = { togglePlayPause() },
                    onNext = { spotifyAppRemote?.playerApi?.skipNext() },
                    onPrevious = { spotifyAppRemote?.playerApi?.skipPrevious() },
                    onSeek = { pos -> spotifyAppRemote?.playerApi?.seekTo(pos) }
                )
            }
        }
    }

    private val AUTH_REQUEST_CODE = 0x10

    override fun onStart() {
        super.onStart()
        SpotifyAppRemote.setDebugMode(true)
        authenticateSpotify()
    }

    // Triggers Spotify's login/consent screen directly from OUR foreground activity.
    // This avoids Android's Background Activity Launch (BAL) block, which stops
    // Spotify's own background service from opening that screen on its own —
    // the exact failure mode behind the endless "Connecting..." we were hitting.
    private fun authenticateSpotify() {
        connectionStatus.value = "Requesting Spotify authorization..."
        val request = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUri)
            .setScopes(arrayOf("app-remote-control", "user-modify-playback-state"))
            .build()
        AuthorizationClient.openLoginActivity(this, AUTH_REQUEST_CODE, request)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == AUTH_REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, intent)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    Log.i("SpotifyMini", "Spotify authorization granted, connecting App Remote")
                    connectAppRemote()
                }
                AuthorizationResponse.Type.ERROR -> {
                    Log.e("SpotifyMini", "Spotify authorization error: ${response.error}")
                    connectionStatus.value = "Auth error: ${response.error}"
                }
                else -> {
                    Log.w("SpotifyMini", "Spotify authorization result: ${response.type}")
                    connectionStatus.value = "Auth cancelled or unknown (${response.type})"
                }
            }
        }
    }

    private fun connectAppRemote() {
        connectionStatus.value = "Connecting to App Remote..."
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                connectionStatus.value = "Connected"
                Log.i("SpotifyMini", "Connected to Spotify App Remote")
                subscribeToPlayerState()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("SpotifyMini", "Spotify connection failed", throwable)
                connectionStatus.value = "Failed: ${throwable.javaClass.simpleName} - ${throwable.message}"
                Toast.makeText(
                    this@MainActivity,
                    "Spotify connection failed: ${throwable.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { state: PlayerState ->
            trackState.value = state.track
            isPaused.value = state.isPaused
            progressMs.value = state.playbackPosition
            durationMs.value = state.track?.duration ?: 1L

            state.track?.let { track ->
                spotifyAppRemote?.imagesApi
                    ?.getImage(track.imageUri, SpotifyImage.Dimension.LARGE)
                    ?.setResultCallback { bitmap ->
                        albumArt.value = bitmap
                        updateBackgroundColorFromArt(bitmap)
                    }
            }
        }
    }

    // Extracts a dark, muted/vibrant color from the album art so the background
    // can shift to match each song, instead of a flat static color.
    private fun updateBackgroundColorFromArt(bitmap: Bitmap?) {
        if (bitmap == null) {
            backgroundColor.value = DEFAULT_BACKGROUND
            return
        }
        Palette.from(bitmap).generate { palette ->
            val swatch = palette?.darkVibrantSwatch
                ?: palette?.darkMutedSwatch
                ?: palette?.dominantSwatch
                ?: palette?.vibrantSwatch
                ?: palette?.mutedSwatch
            backgroundColor.value = swatch?.let { Color(it.rgb) } ?: DEFAULT_BACKGROUND
        }
    }

    private fun togglePlayPause() {
        if (isPaused.value) {
            spotifyAppRemote?.playerApi?.resume()
        } else {
            spotifyAppRemote?.playerApi?.pause()
        }
    }
}

@Composable
fun PlayerScreen(
    track: Track?,
    albumArt: Bitmap?,
    isPaused: Boolean,
    progressMs: Long,
    durationMs: Long,
    connectionStatus: String,
    backgroundColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var sliderPosition by remember { mutableStateOf(progressMs.toFloat()) }
    var userSeeking by remember { mutableStateOf(false) }
    var localProgress by remember { mutableStateOf(progressMs) }

    // Ticks the slider forward locally between PlayerState updates so it moves smoothly
    LaunchedEffect(progressMs, isPaused) {
        localProgress = progressMs
        if (!userSeeking) sliderPosition = localProgress.toFloat()
        while (!isPaused) {
            delay(500)
            localProgress += 500
            if (!userSeeking) sliderPosition = localProgress.toFloat()
        }
    }

    // Smoothly cross-fades the background color as it changes between songs
    val animatedBackground by animateColorAsState(
        targetValue = backgroundColor,
        label = "backgroundColor"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBackground)
    ) {
        if (isLandscape) {
            LandscapeLayout(
                track = track,
                albumArt = albumArt,
                isPaused = isPaused,
                sliderPosition = sliderPosition,
                durationMs = durationMs,
                connectionStatus = connectionStatus,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSliderChange = { userSeeking = true; sliderPosition = it },
                onSliderChangeFinished = { userSeeking = false; onSeek(sliderPosition.toLong()) }
            )
        } else {
            PortraitLayout(
                track = track,
                albumArt = albumArt,
                isPaused = isPaused,
                sliderPosition = sliderPosition,
                durationMs = durationMs,
                connectionStatus = connectionStatus,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSliderChange = { userSeeking = true; sliderPosition = it },
                onSliderChangeFinished = { userSeeking = false; onSeek(sliderPosition.toLong()) }
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    track: Track?,
    albumArt: Bitmap?,
    isPaused: Boolean,
    sliderPosition: Float,
    durationMs: Long,
    connectionStatus: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = connectionStatus, fontSize = 12.sp, color = Color.LightGray)
        Spacer(modifier = Modifier.height(12.dp))

        AlbumArtView(albumArt = albumArt, size = 280.dp)

        Spacer(modifier = Modifier.height(24.dp))

        TrackInfo(track = track, alignment = Alignment.CenterHorizontally)

        Spacer(modifier = Modifier.height(16.dp))

        PlaybackSlider(
            sliderPosition = sliderPosition,
            durationMs = durationMs,
            onSliderChange = onSliderChange,
            onSliderChangeFinished = onSliderChangeFinished
        )

        Spacer(modifier = Modifier.height(24.dp))

        PlaybackControls(
            isPaused = isPaused,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious
        )
    }
}

@Composable
private fun LandscapeLayout(
    track: Track?,
    albumArt: Bitmap?,
    isPaused: Boolean,
    sliderPosition: Float,
    durationMs: Long,
    connectionStatus: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        AlbumArtView(albumArt = albumArt, size = 220.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = connectionStatus, fontSize = 12.sp, color = Color.LightGray)
            Spacer(modifier = Modifier.height(12.dp))

            TrackInfo(track = track, alignment = Alignment.Start)

            Spacer(modifier = Modifier.height(16.dp))

            PlaybackSlider(
                sliderPosition = sliderPosition,
                durationMs = durationMs,
                onSliderChange = onSliderChange,
                onSliderChangeFinished = onSliderChangeFinished
            )

            Spacer(modifier = Modifier.height(24.dp))

            PlaybackControls(
                isPaused = isPaused,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }
    }
}

@Composable
private fun AlbumArtView(albumArt: Bitmap?, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.DarkGray)
    ) {
        albumArt?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Album art",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TrackInfo(track: Track?, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        Text(
            text = track?.name ?: "Nothing playing",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
        Text(
            text = track?.artist?.name ?: "",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1
        )
    }
}

@Composable
private fun PlaybackSlider(
    sliderPosition: Float,
    durationMs: Long,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit
) {
    Column {
        Slider(
            value = sliderPosition,
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatMs(sliderPosition.toLong()), fontSize = 12.sp, color = Color.White)
            Text(text = formatMs(durationMs), fontSize = 12.sp, color = Color.White)
        }
    }
}

@Composable
private fun PlaybackControls(
    isPaused: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}