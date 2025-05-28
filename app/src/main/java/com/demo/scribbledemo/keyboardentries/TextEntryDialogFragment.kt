package com.demo.scribbledemo.keyboardentries

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.demo.scribbledemo.scribble.HSVColorSlider.getColor
import com.demo.scribbledemo.scribble.HSVColorSlider.setUpForColor
import com.demo.scribbledemo.R
import com.demo.scribbledemo.util.SimpleColorFilter
import com.demo.scribbledemo.util.ViewUtil
import org.signal.imageeditor.core.HiddenEditText
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer

class TextEntryDialogFragment :
  KeyboardEntryDialogFragment(R.layout.v2_media_image_editor_text_entry_fragment) {

  private lateinit var hiddenTextEntry: HiddenEditText
  private lateinit var controller: Controller

  private var colorIndicatorAlphaAnimator: Animator? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    controller = requireListener()

    hiddenTextEntry = HiddenEditText(requireContext())
    (view as ViewGroup).addView(hiddenTextEntry)

    view.setOnClickListener {
      dismissAllowingStateLoss()
    }
    val arg = requireArguments()
    val element: EditorElement = requireNotNull(
      if (Build.VERSION.SDK_INT >= 33) {
        arg.getParcelable("element", EditorElement::class.java)
      } else {
        @Suppress("DEPRECATION")
        arg.getParcelable("element")
      }
    )
    val incognito = requireArguments().getBoolean("incognito")
    val selectAll = requireArguments().getBoolean("selectAll")

    hiddenTextEntry.setCurrentTextEditorElement(element)
    hiddenTextEntry.setIncognitoKeyboardEnabled(incognito)

    if (selectAll) {
      hiddenTextEntry.selectAll()
    }

    hiddenTextEntry.setOnEditOrSelectionChange { editorElement, textRenderer ->
      controller.zoomToFitText(editorElement, textRenderer)
    }

    hiddenTextEntry.setOnEndEdit {
      dismissAllowingStateLoss()
    }

    focusAndShowKeyboard(hiddenTextEntry)

    val slider: AppCompatSeekBar = view.findViewById(R.id.image_editor_hud_draw_color_bar)
    val colorIndicator: ImageView = view.findViewById(R.id.image_editor_hud_color_indicator)
    val styleToggle: ImageView = view.findViewById(R.id.image_editor_hud_text_style_button)

    colorIndicator.background = AppCompatResources.getDrawable(
      requireContext(),
      R.drawable.ic_color_preview
    )

    slider.setUpForColor(
      Color.WHITE,
      {
        colorIndicator.drawable.colorFilter = SimpleColorFilter(slider.getColor())
        colorIndicator.translationX = (slider.thumb.bounds.left.toFloat() + ViewUtil.dpToPx(
          16
        ))
        controller.onTextColorChange(slider.progress)
      },
      {
        colorIndicatorAlphaAnimator?.end()
        colorIndicatorAlphaAnimator = ObjectAnimator.ofFloat(
          colorIndicator,
          "alpha",
          colorIndicator.alpha,
          1f
        )
        colorIndicatorAlphaAnimator?.duration = 150L
        colorIndicatorAlphaAnimator?.start()
      },
      {
        colorIndicatorAlphaAnimator?.end()
        colorIndicatorAlphaAnimator = ObjectAnimator.ofFloat(
          colorIndicator,
          "alpha",
          colorIndicator.alpha,
          0f
        )
        colorIndicatorAlphaAnimator?.duration = 150L
        colorIndicatorAlphaAnimator?.start()
      }
    )

    slider.progress = requireArguments().getInt("color_index")

    styleToggle.setOnClickListener {
      (element.renderer as MultiLineTextRenderer).nextMode()
    }
  }

  private inline fun <reified T> Fragment.requireListener(): T {
    val hierarchy = mutableListOf<String>()
    try {
      var parent: Fragment? = parentFragment
      while (parent != null) {
        if (parent is T) {
          return parent
        }
        hierarchy.add(parent::class.java.name)
        parent = parent.parentFragment
      }

      return activity as T
    } catch (e: ClassCastException) {
      hierarchy.add(activity?.let { it::class.java.name } ?: "<null activity>")
      throw ListenerNotFoundException(hierarchy, e)
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    controller.onTextEntryDialogDismissed(!hiddenTextEntry.text.isNullOrEmpty())
  }

  interface Controller {
    fun onTextEntryDialogDismissed(hasText: Boolean)
    fun zoomToFitText(editorElement: EditorElement, textRenderer: MultiLineTextRenderer)
    fun onTextColorChange(colorIndex: Int)
  }

  companion object {
    fun show(
      fragmentManager: FragmentManager,
      editorElement: EditorElement,
      isIncognitoEnabled: Boolean,
      selectAll: Boolean,
      colorIndex: Int
    ) {
      val args = Bundle().apply {
        putParcelable("element", editorElement)
        putBoolean("incognito", isIncognitoEnabled)
        putBoolean("selectAll", selectAll)
        putInt("color_index", colorIndex)
      }

      TextEntryDialogFragment().apply {
        arguments = args
        show(fragmentManager, "text_entry")
      }
    }

    fun focusAndShowKeyboard(view: View) {
      view.requestFocus()
      if (view.hasWindowFocus()) {
        showTheKeyboardNow(view)
      } else {
        view.viewTreeObserver.addOnWindowFocusChangeListener(object :
          OnWindowFocusChangeListener {
          override fun onWindowFocusChanged(hasFocus: Boolean) {
            if (hasFocus) {
              showTheKeyboardNow(view)
              view.viewTreeObserver.removeOnWindowFocusChangeListener(this)
            }
          }
        })
      }
    }

    private fun showTheKeyboardNow(view: View) {
      if (view.isFocused) {
        view.post {
          val inputMethodManager: InputMethodManager = getInputMethodManager(
            view.context
          )
          inputMethodManager.showSoftInput(
            view,
            InputMethodManager.SHOW_IMPLICIT
          )
        }
      }
    }

    fun getInputMethodManager(context: Context): InputMethodManager {
      return context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

  }
}

class ListenerNotFoundException(hierarchy: List<String>, cause: Throwable) :
  Exception(formatMessage(hierarchy), cause) {
  companion object {
    fun formatMessage(hierarchy: List<String>): String {
      return "Hierarchy Searched: \n${hierarchy.joinToString("\n")}"
    }
  }
}