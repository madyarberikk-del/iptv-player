package com.iptv.player

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.iptv.player.databinding.ActivityYoutubeBinding

/** Просмотр видео YouTube через официальный встраиваемый плеер (YouTube IFrame Player API). */
class YouTubeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"

        /** Origin, от имени которого встраивается плеер (YouTube требует его для WebView). */
        private const val ORIGIN = "https://com.iptv.player"
    }

    private lateinit var b: ActivityYoutubeBinding
    private lateinit var url: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityYoutubeBinding.inflate(layoutInflater)
        setContentView(b.root)

        url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val videoId = YouTube.videoId(url)
        if (videoId == null) { finish(); return }
        Storage(this).lastChannelUrl = url

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, b.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        b.openInApp.setOnClickListener { openInYouTubeApp() }
        b.close.setOnClickListener { finish() }

        with(b.web) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                // Переходы по ссылкам внутри плеера (логотип, «смотреть на YouTube») — во внешнее приложение
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    return true
                }
            }
            addJavascriptInterface(Bridge(), "Android")
            loadDataWithBaseURL("$ORIGIN/", playerHtml(videoId), "text/html", "utf-8", null)
        }
    }

    override fun onPause() {
        super.onPause()
        b.web.onPause()
    }

    override fun onResume() {
        super.onResume()
        b.web.onResume()
    }

    override fun onDestroy() {
        if (::b.isInitialized) b.web.destroy()
        super.onDestroy()
    }

    private fun showError(code: Int) {
        b.errorText.text = getString(
            when (code) {
                2 -> R.string.yt_error_bad_id
                100 -> R.string.yt_error_not_found
                101, 150 -> R.string.yt_error_embed_disabled
                else -> R.string.yt_error_other
            }, code
        )
        b.errorPanel.isVisible = true
    }

    private fun openInYouTubeApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            b.errorText.setText(R.string.yt_no_app)
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onError(code: Int) = runOnUiThread { showError(code) }
    }

    private fun playerHtml(videoId: String) = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
          #player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
        </style>
        </head><body>
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
          function onYouTubeIframeAPIReady() {
            new YT.Player('player', {
              width: '100%', height: '100%',
              videoId: '$videoId',
              playerVars: { autoplay: 1, playsinline: 1, rel: 0, fs: 0, origin: '$ORIGIN' },
              events: {
                onReady: function (e) { e.target.playVideo(); },
                onError: function (e) { Android.onError(e.data); }
              }
            });
          }
        </script>
        </body></html>
    """.trimIndent()
}
