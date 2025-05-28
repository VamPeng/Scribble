/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.demo.scribbledemo.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


public final class ViewUtil {

  private ViewUtil() {
  }

  public static float pxToDp(float px) {
    return px / Resources.getSystem().getDisplayMetrics().density;
  }

  public static int dpToPx(Context context, int dp) {
    return (int)((dp * context.getResources().getDisplayMetrics().density) + 0.5);
  }

  public static int dpToPx(int dp) {
    return Math.round(dp * Resources.getSystem().getDisplayMetrics().density);
  }

  public static int dpToSp(int dp) {
    return (int) (dpToPx(dp) / Resources.getSystem().getDisplayMetrics().scaledDensity);
  }

  public static int spToPx(float sp) {
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, Resources.getSystem().getDisplayMetrics());
  }

  public static int getStatusBarHeight(@NonNull View view) {
    final WindowInsetsCompat rootWindowInsets = ViewCompat.getRootWindowInsets(view);
    if (Build.VERSION.SDK_INT > 29 && rootWindowInsets != null) {
      return rootWindowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
    } else {
      int result     = 0;
      int resourceId = view.getResources().getIdentifier("status_bar_height", "dimen", "android");
      if (resourceId > 0) {
        result = view.getResources().getDimensionPixelSize(resourceId);
      }
      return result;
    }
  }

}
