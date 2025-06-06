package com.demo.scribbledemo.media

import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.util.LinkedList

class MediaReviewFragmentPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

  private val mediaList: MutableList<Media> = mutableListOf()

  fun submitMedia(media: List<Media>) {
    val oldMedia: List<Media> = LinkedList(mediaList)
    mediaList.clear()
    mediaList.addAll(media)

    DiffUtil.calculateDiff(Callback(oldMedia, mediaList))
      .dispatchUpdatesTo(this)
  }

  override fun getItemId(position: Int): Long {
    if (position > mediaList.size || position < 0) {
      return RecyclerView.NO_ID
    }

    return mediaList[position].uri.hashCode().toLong()
  }

  override fun containsItem(itemId: Long): Boolean {
    return mediaList.any { it.uri.hashCode().toLong() == itemId }
  }

  override fun getItemCount(): Int = mediaList.size

  override fun createFragment(position: Int): Fragment {
    val mediaItem: Uri = mediaList[position].uri

    return MediaReviewImagesPageFragment.newInstance(mediaItem)
  }

  private class Callback(
    private val oldList: List<Media>,
    private val newList: List<Media>
  ) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition].uri == newList[newItemPosition].uri
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition].uri == newList[newItemPosition].uri
    }
  }
}