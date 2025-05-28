package com.demo.scribbledemo.media

import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.parcelize.Parcelize


/**
 * 2025/5/15 14:47
 *
 * author: yuhuipeng
 *
 * description:
 */
class MediaSelectionViewModel : ViewModel() {

  private val internalHudCommands = PublishSubject.create<HudCommand>()

  val hudCommands: Observable<HudCommand> = internalHudCommands

  private val store: MutableStateFlow<MediaSelectionState> = MutableStateFlow(MediaSelectionState())

  val state: StateFlow<MediaSelectionState> = store

  fun sendCommand(hudCommand: HudCommand) {
    internalHudCommands.onNext(hudCommand)
  }

  fun addMedia(medias: List<Media>) {
    addMedia(medias.toSet())
  }

  fun addMedia(media: Media) {
    addMedia(setOf(media))
  }

  private fun addMedia(media: Set<Media>) {
    val newSelectionList: List<Media> = linkedSetOf<Media>().apply {
      addAll(store.value.selectedMedia)
      addAll(media)
    }.toList()

    store.update {
      it.copy(
        selectedMedia = newSelectionList,
        focusedMedia = it.focusedMedia ?: newSelectionList.first(),
      )
    }
  }

  fun setTouchEnabled(isEnabled: Boolean) {
    store.update { it.copy(isTouchEnabled = isEnabled) }
  }

  fun onPageChanged(media: Media) {
    store.update { it.copy(focusedMedia = media) }
  }

  fun onPageChanged(position: Int) {
    store.update {
      if (position >= it.selectedMedia.size) {
        it.copy(focusedMedia = null)
      } else {
        val focusedMedia: Media = it.selectedMedia[position]
        it.copy(focusedMedia = focusedMedia)
      }
    }
  }

  fun getEditorState(uri: Uri): Any? {
    return store.value.editorStateMap[uri]
  }

  fun setEditorState(uri: Uri, state: Any) {
    store.update {
      it.copy(editorStateMap = it.editorStateMap + (uri to state))
    }
  }

}

data class MediaSelectionState(
  val selectedMedia: List<Media> = listOf(),
  val focusedMedia: Media? = null,
  val isTouchEnabled: Boolean = true,
  val editorStateMap: Map<Uri, Any> = mapOf(),
)

@Parcelize
class Media(
  val uri: Uri
) : Parcelable
