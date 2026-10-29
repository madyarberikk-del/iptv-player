package com.iptv.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.iptv.player.databinding.ItemChannelBinding

class ChannelAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Channel) -> Unit,
) : ListAdapter<Channel, ChannelAdapter.Holder>(Diff) {

    var favorites: Set<String> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

    var lastUrl: String? = null

    class Holder(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ch = getItem(position)
        with(holder.b) {
            name.text = ch.name
            group.text = ch.group
            group.isVisible = ch.group.isNotEmpty()
            star.isVisible = ch.url in favorites
            nowMark.isVisible = ch.url == lastUrl
            logo.load(ch.logo) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                fallback(R.drawable.ic_tv)
            }
            root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
            root.setOnLongClickListener { onLongClick(ch); true }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.url == b.url && a.name == b.name
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
}
