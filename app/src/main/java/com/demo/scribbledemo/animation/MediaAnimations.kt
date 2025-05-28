package com.demo.scribbledemo.animation

import android.view.animation.Interpolator
import androidx.core.view.animation.PathInterpolatorCompat

object MediaAnimations {
  /**
   * Fast-In-Extra-Slow-Out Interpolator
   */
  @JvmStatic
  val interpolator: Interpolator = createDefaultCubicBezierInterpolator()

  @JvmStatic
  fun createDefaultCubicBezierInterpolator(): Interpolator = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)

}
