package com.demo.scribbledemo

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.demo.scribbledemo.media.Media
import com.demo.scribbledemo.media.MediaReviewFragment
import com.demo.scribbledemo.media.MediaSelectionViewModel


/**
 * 2025/5/14 16:56
 *
 * author: yuhuipeng
 *
 * description:
 */
class EditorActivity : AppCompatActivity() {

  val TAG = this::class.simpleName

  private lateinit var mediaPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
  private val sharedViewModel by viewModels<MediaSelectionViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.editor_activity)

    // 初始化 launcher
    mediaPickerLauncher = registerForActivityResult(
      ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
      // 显示或处理选中的文件
      Log.d("MediaPicker", "Selected media URI: ${uris}")

      sharedViewModel.addMedia(uris.map { uri -> Media(uri) })

      if (uris.isEmpty()) {
        Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show()
      }
    }

    supportFragmentManager.beginTransaction().replace(
      R.id.fragment_container,
      MediaReviewFragment.newInstance()
    ).commit()

    findViewById<View>(R.id.btn_add).setOnClickListener {
      mediaPickerLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
      )
    }
  }
}

val mockUri = Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/1000026158")
val mockUri2 = Uri.parse("content://media/external/images/media/1000026070")