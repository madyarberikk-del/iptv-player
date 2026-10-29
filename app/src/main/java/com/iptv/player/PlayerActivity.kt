package com.iptv.player

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.iptv.player.databinding.ActivityPlayerBinding

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        /** Список каналов для переключения «вперёд/назад» (передаётся из MainActivity). */
        var queue: List<Channel> = emptyList()
    }

    private lateinit var b: ActivityPlayerBinding
    private lateinit var storage: Storage
    private var player: ExoPlayer? = null
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (queue.isEmpty()) { finish(); return }

        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        storage = Storage(this)
        index = savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, b.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        b.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { b.topBar.visibility = it }
        )
        b.back.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        if (queue.isEmpty()) return
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        player?.let { index = it.currentMediaItemIndex; it.release() }
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, player?.currentMediaItemIndex ?: index)
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Storage.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        val items = queue.map { ch ->
            MediaItem.Builder()
                .setUri(ch.url)
                .setMediaId(ch.url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
                .apply { if (ch.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { p ->
                b.playerView.player = p
                p.addListener(listener)
                p.setMediaItems(items, index.coerceIn(items.indices), 0L)
                p.prepare()
                p.playWhenReady = true
            }
        updateTitle()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updateTitle()

        override fun onPlayerError(error: PlaybackException) {
            b.progress.visibility = View.GONE
            Toast.makeText(
                this@PlayerActivity,
                getString(R.string.channel_unavailable, error.errorCodeName),
                Toast.LENGTH_LONG,
            ).show()
        }

        override fun onPlaybackStateChanged(state: Int) {
            b.progress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
        }
    }

    private fun updateTitle() {
        val p = player ?: return
        val ch = queue.getOrNull(p.currentMediaItemIndex) ?: return
        b.title.text = ch.name
        storage.lastChannelUrl = ch.url
    }
}
