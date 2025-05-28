package com.demo.scribbledemo.media

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.demo.scribbledemo.scribble.ImageEditorFragment
import com.demo.scribbledemo.scribble.ImageEditorHudV2
import com.demo.scribbledemo.R
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit

private val MODE_DELAY = TimeUnit.MILLISECONDS.toMillis(300)
private const val IMAGE_EDITOR_TAG = "image.editor.fragment"

/**
 * 2025/5/15 13:54
 *
 * author: yuhuipeng
 *
 * description:
 */
class MediaReviewImagesPageFragment : Fragment(R.layout.fragment_container), ImageEditorFragment.Controller {

  private var imageEditorFragment: ImageEditorFragment? = null
  private var hudCommandDisposable: Disposable = Disposable.disposed()

  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    imageEditorFragment = ensureImageEditorFragment()
  }

  override fun onPause() {
    super.onPause()
    hudCommandDisposable.dispose()
  }

  override fun onResume() {
    super.onResume()
    hudCommandDisposable = sharedViewModel.hudCommands.subscribe { command ->
      if (isResumed) {
        when (command) {
          HudCommand.StartDraw -> {
            sharedViewModel.setTouchEnabled(false)
            requireView().postDelayed(
              {
                imageEditorFragment?.setMode(ImageEditorHudV2.Mode.DRAW)
              },
              MODE_DELAY
            )
          }

          HudCommand.StartCropAndRotate -> {
            sharedViewModel.setTouchEnabled(false)
            requireView().postDelayed(
              {
                imageEditorFragment?.setMode(ImageEditorHudV2.Mode.CROP)
              },
              MODE_DELAY
            )
          }

          HudCommand.SaveMedia -> imageEditorFragment?.onSave()
          else -> Unit
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    imageEditorFragment?.let {
      sharedViewModel.setEditorState(requireUri(), requireNotNull(it.saveState()))
    }
  }

  private fun ensureImageEditorFragment(): ImageEditorFragment {
    val fragmentInManager: ImageEditorFragment? = childFragmentManager.findFragmentByTag(
      IMAGE_EDITOR_TAG
    ) as? ImageEditorFragment

    return if (fragmentInManager != null) {
      fragmentInManager
    } else {
      val imageEditorFragment = ImageEditorFragment.newInstance(
        requireUri()
      )
      childFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          imageEditorFragment,
          IMAGE_EDITOR_TAG
        )
        .commitAllowingStateLoss()
      imageEditorFragment
    }
  }

  private fun requireUri(): Uri = requireNotNull(
    requireArguments().getParcelableCompat(
      ARG_URI,
      Uri::class.java
    )
  )

  private fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= 33) {
      this.getParcelable(key, clazz)
    } else {
      @Suppress("DEPRECATION")
      this.getParcelable(key)
    }
  }

  companion object {
    private const val ARG_URI = "arg.uri"

    fun newInstance(uri: Uri): Fragment {
      return MediaReviewImagesPageFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_URI, uri)
        }
      }
    }
  }

  override fun onTouchEventsNeeded(needed: Boolean) {
    if (isResumed) {
      if (!needed) {
        requireView().postDelayed(
          {
            sharedViewModel.setTouchEnabled(true)
          },
          MODE_DELAY
        )
      } else {
        sharedViewModel.setTouchEnabled(false)
      }
    }
  }

  override fun onDoneEditing() {
    imageEditorFragment?.setMode(ImageEditorHudV2.Mode.NONE)
    if (isResumed) {
      imageEditorFragment?.let {
        sharedViewModel.setEditorState(requireUri(), requireNotNull(it.saveState()))
      }
    }
  }

  override fun onCancelEditing() {
    restoreState()
  }

  override fun restoreState() {
    val data = sharedViewModel.getEditorState(requireUri()) as? ImageEditorFragment.Data
    if (data != null) {
      imageEditorFragment?.restoreState(data)
    } else {
      imageEditorFragment?.onClearAll()
    }
  }

}