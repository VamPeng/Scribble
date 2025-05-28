package com.demo.scribbledemo.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.annimon.stream.Stream


/**
 * 2025/5/19 15:59
 *
 * author: yuhuipeng
 *
 * description:
 */
object Permissions{

  fun canWriteToMediaStore(context: Context): Boolean {
    return Build.VERSION.SDK_INT > 28 ||
      hasAll(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
  }

  fun hasAll(context: Context, vararg permissions: String?): Boolean {
    return Build.VERSION.SDK_INT >= 23 ||
      Stream.of<String>(*permissions).allMatch { permission: String? -> ContextCompat.checkSelfPermission(context, permission!!) == PackageManager.PERMISSION_GRANTED }
  }

}