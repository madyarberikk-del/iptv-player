package com.iptv.player

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.iptv.player.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Просмотр потоков на движке VLC: декодирует почти любые форматы IPTV (MPEG-2, H.264, H.265 в TS/HLS).
 * Сначала пробует аппаратный декодер телефона, при неудаче — программный.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        /** Список каналов для переключения «вперёд/назад» (передаётся из MainActivity). */
        var queue: List<Channel> = emptyList()

        private const val CONTROLS_TIMEOUT_MS = 4000L
        private val SCALES = arrayOf(
            MediaPlayer.ScaleType.SURFACE_BEST_FIT,
            MediaPlayer.ScaleType.SURFACE_FIT_SCREEN,
            MediaPlayer.ScaleType.SURFACE_FILL,
        )
    }

    private lateinit var b: ActivityPlayerBinding
    private lateinit var storage: Storage
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var index = 0
    private var scale = 0

    private val hideControls = Runnable { showControls(false) }

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

        b.root.setOnClickListener { showControls(!b.topBar.isVisible) }
        b.back.setOnClickListener { finish() }
        b.prev.setOnClickListener { play(index - 1) }
        b.next.setOnClickListener { play(index + 1) }
        b.playPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            showControls(true)
        }
        b.aspect.setOnClickListener {
            scale = (scale + 1) % SCALES.size
            player?.videoScale = SCALES[scale]
            showControls(true)
        }
    }

    override fun onStart() {
        super.onStart()
        if (queue.isEmpty()) return
        val vlc = LibVLC(this, arrayListOf("--network-caching=2000", "--http-reconnect"))
        libVlc = vlc
        player = MediaPlayer(vlc).also {
            it.attachViews(b.videoLayout, null, false, false)
            it.videoScale = SCALES[scale]
            it.setEventListener(::onPlayerEvent)
        }
        play(index)
    }

    override fun onStop() {
        super.onStop()
        b.root.removeCallbacks(hideControls)
        player?.run {
            setEventListener(null)
            stop()
            detachViews()
            release()
        }
        player = null
        libVlc?.release()
        libVlc = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
    }

    private fun play(i: Int) {
        val p = player ?: return
        val vlc = libVlc ?: return
        index = (i + queue.size) % queue.size
        val ch = queue[index]
        b.title.text = ch.name
        storage.lastChannelUrl = ch.url

        val media = Media(vlc, Uri.parse(ch.url)).apply {
            // true, false — аппаратное декодирование с откатом на программное
            setHWDecoderEnabled(true, false)
            addOption(":http-user-agent=${Storage.USER_AGENT}")
        }
        p.media = media
        media.release()
        p.play()

        b.progress.isVisible = true
        showControls(true)
    }

    private fun onPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Buffering -> b.progress.isVisible = event.buffering < 100f
            MediaPlayer.Event.Playing -> {
                b.progress.isVisible = false
                b.playPause.setImageResource(R.drawable.ic_pause)
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached ->
                b.playPause.setImageResource(R.drawable.ic_play)
            MediaPlayer.Event.EncounteredError -> {
                b.progress.isVisible = false
                b.playPause.setImageResource(R.drawable.ic_play)
                Toast.makeText(
                    this,
                    getString(R.string.channel_unavailable, queue[index].name),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun showControls(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        b.topBar.visibility = v
        b.bottomBar.visibility = v
        b.root.removeCallbacks(hideControls)
        if (show) b.root.postDelayed(hideControls, CONTROLS_TIMEOUT_MS)
    }
}
