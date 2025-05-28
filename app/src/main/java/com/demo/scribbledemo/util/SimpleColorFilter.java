package com.demo.scribbledemo.util;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.ColorInt;

public class SimpleColorFilter extends PorterDuffColorFilter {
    public SimpleColorFilter(@ColorInt int color) {
        super(color, PorterDuff.Mode.SRC_ATOP);
    }
}