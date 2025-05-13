package com.demo.scribbledemo.scribble

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import androidx.core.view.animation.PathInterpolatorCompat
import org.signal.imageeditor.core.Bounds

class BrushWidthPreviewView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val tmpRect = Rect()

  private var animator: Animator? = null

  private val backdropPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
    color = Color.WHITE
  }

  private val brushPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
    color = Color.WHITE
  }

  private var radius: Float = (0.03f * Bounds.FULL_BOUNDS.width() / 2f)
  private var isBlur: Boolean = false

  fun setBlur(blur: Boolean) {
    isBlur = blur
    invalidate()
  }

  fun setColor(@ColorInt color: Int) {
    brushPaint.color = color
    invalidate()
  }

  fun setThickness(thickness: Float) {
    radius = (thickness * Bounds.FULL_BOUNDS.width()) / 2f
    invalidate()
  }

  fun show() {
    visible = true
    animator?.cancel()
    animator = ObjectAnimator.ofFloat(this, "alpha", 1f).apply {
      interpolator = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)
      duration = 150L
      start()
    }
  }

  fun hide() {
    animator?.cancel()
    animator = ObjectAnimator.ofFloat(this, "alpha", 0f).apply {
      interpolator = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)
      duration = 150L
      doOnEnd { visible = false }
      start()
    }
  }

  override fun onDraw(canvas: Canvas) {
    canvas.getClipBounds(tmpRect)
    canvas.drawCircle(tmpRect.exactCenterX(), tmpRect.exactCenterY(), radius + ViewUtil.dpToPx(1), backdropPaint)

    if (!isBlur) {
      canvas.drawCircle(tmpRect.exactCenterX(), tmpRect.exactCenterY(), radius, brushPaint)
    }
  }
}

private var View.visible: Boolean
  get() {
    return this.visibility == View.VISIBLE
  }
  set(value) {
    this.visibility = if (value) View.VISIBLE else View.GONE
  }
