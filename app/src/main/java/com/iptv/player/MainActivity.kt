package com.iptv.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iptv.player.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var storage: Storage
    private lateinit var adapter: ChannelAdapter

    private var allChannels: List<Channel> = emptyList()
    private var shown: List<Channel> = emptyList()
    private var query = ""
    private var group: String? = null
    private var favoritesOnly = false

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        storage = Storage(this)
        adapter = ChannelAdapter(onClick = ::play, onLongClick = ::toggleFavorite)
        b.list.adapter = adapter
        b.swipe.setOnRefreshListener { refresh() }
        b.addButton.setOnClickListener { askUrl() }

        b.groups.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                group = if (pos == 0) null else p?.getItemAtPosition(pos) as String
                applyFilter()
            }
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }

        storage.loadCachedPlaylist()?.let { showPlaylist(M3uParser.parse(it)) } ?: showPlaylist(emptyList())
    }

    override fun onResume() {
        super.onResume()
        adapter.lastUrl = storage.lastChannelUrl
        adapter.favorites = storage.favorites
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        val search = menu.findItem(R.id.action_search).actionView as SearchView
        search.queryHint = getString(R.string.search_hint)
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                query = q.orEmpty().trim()
                applyFilter()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_url -> askUrl()
            R.id.action_open_file -> pickFile.launch(arrayOf("*/*"))
            R.id.action_refresh -> { b.swipe.isRefreshing = true; refresh() }
            R.id.action_favorites -> {
                favoritesOnly = !favoritesOnly
                item.isChecked = favoritesOnly
                item.setIcon(if (favoritesOnly) R.drawable.ic_star else R.drawable.ic_star_outline)
                applyFilter()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun askUrl() {
        val input = EditText(this).apply {
            hint = "http://…/playlist.m3u"
            setText(storage.playlistUrl.orEmpty())
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_playlist)
            .setView(box)
            .setPositiveButton(R.string.load) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) loadFromUrl(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        val url = storage.playlistUrl
        if (url == null) {
            b.swipe.isRefreshing = false
            toast(getString(R.string.no_url))
        } else {
            loadFromUrl(url)
        }
    }

    private fun loadFromUrl(url: String) {
        b.swipe.isRefreshing = true
        lifecycleScope.launch {
            try {
                val text = Storage.download(url)
                val channels = M3uParser.parse(text)
                if (channels.isEmpty()) error(getString(R.string.no_channels_in_file))
                storage.savePlaylist(text)
                storage.playlistUrl = url
                showPlaylist(channels)
                toast(getString(R.string.loaded_n, channels.size))
            } catch (e: Exception) {
                toast(getString(R.string.load_error, e.message ?: e.javaClass.simpleName))
            } finally {
                b.swipe.isRefreshing = false
            }
        }
    }

    private fun loadFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                val channels = M3uParser.parse(text)
                if (channels.isEmpty()) error(getString(R.string.no_channels_in_file))
                storage.savePlaylist(text)
                storage.playlistUrl = null
                showPlaylist(channels)
                toast(getString(R.string.loaded_n, channels.size))
            } catch (e: Exception) {
                toast(getString(R.string.load_error, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun showPlaylist(channels: List<Channel>) {
        allChannels = channels
        val groups = channels.map { it.group }.filter { it.isNotEmpty() }.distinct()
        b.groups.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.all_groups)) + groups,
        )
        b.groups.isVisible = groups.isNotEmpty()
        group = null
        applyFilter()

        val lastIndex = shown.indexOfFirst { it.url == storage.lastChannelUrl }
        if (lastIndex > 0) b.list.scrollToPosition(lastIndex)
    }

    private fun applyFilter() {
        val fav = storage.favorites
        shown = allChannels.filter { ch ->
            (group == null || ch.group == group) &&
                (!favoritesOnly || ch.url in fav) &&
                (query.isEmpty() || ch.name.contains(query, ignoreCase = true))
        }
        adapter.submitList(shown)
        b.empty.isVisible = shown.isEmpty()
        b.empty.setText(
            when {
                allChannels.isEmpty() -> R.string.empty_no_playlist
                favoritesOnly && fav.isEmpty() -> R.string.empty_no_favorites
                else -> R.string.empty_not_found
            }
        )
        b.addButton.isVisible = allChannels.isEmpty()
        supportActionBar?.subtitle =
            if (allChannels.isEmpty()) null else getString(R.string.channels_n, shown.size)
    }

    private fun play(position: Int) {
        if (position !in shown.indices) return
        PlayerActivity.queue = shown
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, position))
    }

    private fun toggleFavorite(ch: Channel) {
        val added = storage.toggleFavorite(ch.url)
        adapter.favorites = storage.favorites
        toast(getString(if (added) R.string.fav_added else R.string.fav_removed, ch.name))
        if (favoritesOnly) applyFilter()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
