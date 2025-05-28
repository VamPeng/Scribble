package com.demo.scribbledemo.util

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext
import java.util.Locale

/**
 * RenderContext TypefaceProvider that provides typefaces using TextFont.
 */
class FontTypefaceProvider : RendererContext.TypefaceProvider {

  private var cachedTypeface: Typeface? = null
  private var cachedLocale: Locale? = null

  override fun getSelectedTypeface(context: Context, renderer: Renderer, invalidate: RendererContext.Invalidate): Typeface {
    return getTypeface()
  }

  private fun getTypeface(): Typeface {
    return if (Build.VERSION.SDK_INT < 26) {
      Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    } else {
      Typeface.Builder("")
        .setFallback("sans-serif")
        .setWeight(900)
        .build()
    }
  }
}
