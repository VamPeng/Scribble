package com.demo.scribbledemo.scribble

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.constraintlayout.widget.Guideline
import androidx.core.animation.doOnEnd
import com.demo.scribbledemo.keyvalues.ImageEditorValues
import com.demo.scribbledemo.R
import com.demo.scribbledemo.util.SimpleColorFilter
import com.demo.scribbledemo.scribble.HSVColorSlider.getColor
import com.demo.scribbledemo.scribble.HSVColorSlider.setColor
import com.demo.scribbledemo.scribble.HSVColorSlider.setUpForColor
import com.demo.scribbledemo.animation.MediaAnimations
import com.demo.scribbledemo.util.ViewUtil
import com.demo.scribbledemo.util.visible
import java.util.Locale

class ImageEditorHudV2 @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  private var listener: EventListener? = null
  private var currentMode: Mode = Mode.NONE
  private var undoAvailability: Boolean = false
//  private var isAvatarEdit: Boolean = false

  init {
    inflate(context, R.layout.v2_media_image_editor_hud, this)
  }

  private val undoButton: View = findViewById(R.id.image_editor_hud_undo)
  private val clearAllButton: View = findViewById(R.id.image_editor_hud_clear_all)
  private val cancelButton: View = findViewById(R.id.image_editor_hud_cancel_button)
  private val drawButton: View = findViewById(R.id.image_editor_hud_draw_button)
  private val textButton: View = findViewById(R.id.image_editor_hud_text_button)
  private val stickerButton: View = findViewById(R.id.image_editor_hud_sticker_button)  // 贴纸暂时不用
  private val blurButton: View = findViewById(R.id.image_editor_hud_blur_button)
  private val doneButton: View = findViewById(R.id.image_editor_hud_done_button)
  private val drawSeekBar: AppCompatSeekBar = findViewById(R.id.image_editor_hud_draw_color_bar)
  private val brushToggle: ImageView = findViewById(R.id.image_editor_hud_draw_brush)
  private val widthSeekBar: AppCompatSeekBar = findViewById(R.id.image_editor_hud_draw_width_bar)
  private val cropRotateButton: View = findViewById(R.id.image_editor_hud_rotate_button)
  private val cropFlipButton: View = findViewById(R.id.image_editor_hud_flip_button)

  // lock
//  private val cropAspectLockButton: ImageView = findViewById(R.id.image_editor_hud_aspect_lock_button)
  // blur face linearlayout
//  private val blurToggleContainer: View = findViewById(R.id.image_editor_hud_blur_toggle_container)
  // bluer face switch
//  private val blurToggle: MaterialSwitch = findViewById(R.id.image_editor_hud_blur_toggle)
  // toast
//  private val blurToast: View = findViewById(R.id.image_editor_hud_blur_toast)
  private val blurHelpText: View = findViewById(R.id.image_editor_hud_blur_help_text)
  private val colorIndicator: ImageView = findViewById(R.id.image_editor_hud_color_indicator)
  private val bottomGuideline: Guideline = findViewById(R.id.image_editor_bottom_guide)
  private val brushPreview: BrushWidthPreviewView = findViewById(R.id.image_editor_hud_brush_preview)
  private val textStyleToggle: ImageView = findViewById(R.id.image_editor_hud_text_style_button)
  private val rotationDial: RotationDialView = findViewById(R.id.image_editor_hud_crop_rotation_dial)

  private val selectableSet: Set<View> = setOf(drawButton, textButton/*, stickerButton*/, blurButton)

  private val undoTools: Set<View> = setOf(undoButton, clearAllButton)
  private val drawTools: Set<View> = setOf(brushToggle, drawSeekBar, widthSeekBar)
  private val blurTools: Set<View> = setOf(/*blurToggleContainer,*/ blurHelpText, widthSeekBar)
  private val cropTools: Set<View> = setOf(rotationDial)
  private val drawButtonRow: Set<View> = setOf(
    cancelButton,
    doneButton,
    drawButton,
    textButton,
    /*stickerButton,*/
    blurButton
  )
  private val cropButtonRow: Set<View> = setOf(
    cancelButton,
    doneButton,
    cropRotateButton,
    cropFlipButton/*, cropAspectLockButton*/
  )

  private val allModeTools: Set<View> = drawTools + blurTools + drawButtonRow + cropButtonRow + textStyleToggle + cropTools

  private val viewsToSlide: Set<View> = drawButtonRow + cropButtonRow

  //  private val toastDebouncer = Debouncer(3000)
  private var colorIndicatorAlphaAnimator: Animator? = null

  private var modeAnimatorSet: AnimatorSet? = null
  private var undoAnimatorSet: AnimatorSet? = null

  init {
    initializeViews()
    setMode(currentMode)
  }

  private fun initializeViews() {
    colorIndicator.background = AppCompatResources.getDrawable(
      context,
      R.drawable.ic_color_preview
    )

    undoButton.setOnClickListener { listener?.onUndo() }
    clearAllButton.setOnClickListener { listener?.onClearAll() }
    cancelButton.setOnClickListener { listener?.onCancel() }

    textStyleToggle.setOnClickListener { listener?.onTextStyleToggle() }
    drawButton.setOnClickListener { setMode(Mode.DRAW) }
    blurButton.setOnClickListener { setMode(Mode.BLUR) }
    textButton.setOnClickListener { setMode(Mode.TEXT) }
    stickerButton.setOnClickListener { setMode(Mode.INSERT_STICKER) }
    brushToggle.setOnClickListener {
      if (currentMode == Mode.DRAW) {
        setMode(Mode.HIGHLIGHT)
      } else {
        setMode(Mode.DRAW)
      }
    }

    doneButton.setOnClickListener {
      listener?.onDone()
    }

    drawSeekBar.setUpForColor(
      thumbBorderColor = Color.WHITE,
      onColorChanged = {
        updateColorIndicator()
        listener?.onColorChange(getActiveColor())
      },
      onDragStart = {
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
      onDragEnd = {
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

    cropFlipButton.setOnClickListener { listener?.onFlipHorizontal() }
    cropRotateButton.setOnClickListener { listener?.onRotate90AntiClockwise() }

//    cropAspectLockButton.setOnClickListener {
//      listener?.onCropAspectLock()
//      if (listener?.isCropAspectLocked == true) {
//        cropAspectLockButton.setImageResource(R.drawable.symbol_crop_lock_24)
//      } else {
//        cropAspectLockButton.setImageResource(R.drawable.symbol_crop_unlock_24)
//      }
//    }

//    blurToggle.setOnCheckedChangeListener { _, enabled -> listener?.onBlurFacesToggled(enabled) }

    setupWidthSeekBar()

    rotationDial.listener = object : RotationDialView.Listener {
      override fun onDegreeChanged(degrees: Float) {
        listener?.onDialRotationChanged(degrees)
      }

      override fun onGestureStart() {
        listener?.onDialRotationGestureStarted()
      }

      override fun onGestureEnd() {
        listener?.onDialRotationGestureFinished()
      }
    }
  }

  fun setDialRotation(degrees: Float) {
    rotationDial.setDegrees(degrees)
  }

  fun setBottomOfImageEditorView(bottom: Int) {
    bottomGuideline.setGuidelineEnd(bottom)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupWidthSeekBar() {
    widthSeekBar.progressDrawable = AppCompatResources.getDrawable(
      context,
      R.drawable.ic_width_slider_bg
    )
    widthSeekBar.thumb = HSVColorSlider.createThumbDrawable(Color.WHITE)
    widthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        brushPreview.setThickness(getActiveBrushWidth())
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        val progress = seekBar?.progress ?: 0
        listener?.onBrushWidthChange()

        when (currentMode) {
          Mode.DRAW -> ImageEditorValues.setMarkerPercentage(progress)
          Mode.BLUR -> ImageEditorValues.setBlurPercentage(progress)
          Mode.HIGHLIGHT -> ImageEditorValues.setHighlighterPercentage(progress)
          else -> Unit
        }
      }
    })

    widthSeekBar.setOnTouchListener { v, event ->
      if (event.action == MotionEvent.ACTION_DOWN) {
        animateWidthSeekbarIn()
        brushPreview.show()
      } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
        animateWidthSeekbarOut()
        brushPreview.hide()
      }

      v.onTouchEvent(event)
    }
  }

  private fun animateWidthSeekbarIn() {
    widthSeekBar.animate()
      ?.setDuration(ANIMATION_DURATION)
      ?.setInterpolator(MediaAnimations.interpolator)
      ?.translationX(ViewUtil.dpToPx(36).toFloat())
  }

  private fun animateWidthSeekbarOut() {
    widthSeekBar.animate()
      ?.setDuration(ANIMATION_DURATION)
      ?.setInterpolator(MediaAnimations.interpolator)
      ?.translationX(0f)
  }

  fun getActiveColor(): Int {
    return if (currentMode == Mode.HIGHLIGHT) {
//      withHighlighterAlpha(drawSeekBar.getColor())
      withCustomAlpha(drawSeekBar.getColor(), 134)
    } else {
      drawSeekBar.getColor()
    }
  }

  fun getColorIndex(): Int {
    return drawSeekBar.progress
  }

  fun setColorIndex(index: Int) {
    drawSeekBar.progress = index
  }

  fun setActiveColor(color: Int) {
    drawSeekBar.setColor(color or 0xFF000000.toInt())
    updateColorIndicator()
  }

  fun getActiveBrushWidth(): Float {
    val (minimum, maximum) = DRAW_WIDTH_BOUNDARIES[currentMode] ?: throw IllegalStateException("Cannot get width in mode $currentMode")
    return minimum + (maximum - minimum) * (widthSeekBar.progress / 100f)
  }

  fun setBlurFacesToggleEnabled(enabled: Boolean) {
//    blurToggle.setOnCheckedChangeListener(null)
//    blurToggle.isChecked = enabled
//    blurToggle.setOnCheckedChangeListener { _, value -> listener?.onBlurFacesToggled(value) }
  }

  fun hideBlurToast() {
//    blurToast.clearAnimation()
//    blurToast.visible = false
//    toastDebouncer.clear()
  }

  fun setEventListener(eventListener: EventListener?) {
    listener = eventListener
  }

  fun enterMode(mode: Mode) {
    setMode(mode, false)
  }

  fun setMode(mode: Mode) {
    setMode(mode, true)
  }

  fun getMode(): Mode = currentMode

  fun setUndoAvailability(undoAvailability: Boolean) {
    this.undoAvailability = undoAvailability

    if (currentMode != Mode.NONE && currentMode != Mode.DELETE) {
      if (undoAvailability) {
        animateInUndoTools()
      } else {
        animateOutUndoTools()
      }
    }
  }

  private fun setMode(mode: Mode, notify: Boolean) {
    val previousMode: Mode = currentMode
    currentMode = mode
    // updateVisibilities
    clearSelection()
    modeAnimatorSet?.cancel()
    undoAnimatorSet?.cancel()

    when (mode) {
      Mode.NONE -> presentModeNone()
      Mode.CROP -> presentModeCrop()
      Mode.TEXT -> presentModeText()
      Mode.DRAW -> presentModeDraw()
      Mode.BLUR -> presentModeBlur()
      Mode.HIGHLIGHT -> presentModeHighlight()
      Mode.INSERT_STICKER -> presentModeMoveSticker()
      Mode.MOVE_STICKER -> presentModeMoveSticker()
      Mode.DELETE -> presentModeDelete()
      Mode.MOVE_TEXT -> presentModeText()
    }

    if (notify) {
      listener?.onModeStarted(mode, previousMode)
    }

    listener?.onRequestFullScreen(mode != Mode.NONE, mode != Mode.TEXT)
  }

  private fun presentModeNone() {
    animateModeChange(
      inSet = setOf(),
      outSet = allModeTools
    )
    animateOutUndoTools()
  }

  private fun presentModeCrop() {
    animateModeChange(
      inSet = cropTools + cropButtonRow,
      outSet = allModeTools
    )
    animateInUndoTools()
  }

  private fun presentModeDraw() {
    drawButton.isSelected = true
    brushToggle.setImageResource(R.drawable.symbol_brush_pen_24)
    widthSeekBar.progress = ImageEditorValues.getMarkerPercentage()
    listener?.onColorChange(getActiveColor())
    updateColorIndicator()
    animateModeChange(
      inSet = drawButtonRow + drawTools,
      outSet = allModeTools
    )
    animateInUndoTools()
  }

  private fun presentModeHighlight() {
    drawButton.isSelected = true
    brushToggle.setImageResource(R.drawable.symbol_brush_highlighter_24)
    widthSeekBar.progress = ImageEditorValues.getHighlighterPercentage()
    listener?.onColorChange(getActiveColor())
    updateColorIndicator()
    animateModeChange(
      inSet = drawButtonRow + drawTools,
      outSet = allModeTools
    )
    animateInUndoTools()
  }

  private fun presentModeBlur() {
    blurButton.isSelected = true
    widthSeekBar.progress = ImageEditorValues.getBlurPercentage()
    listener?.onColorChange(getActiveColor())
    updateColorIndicator()
    animateModeChange(
      inSet = drawButtonRow + blurTools,
      outSet = allModeTools
    )
    animateInUndoTools()
  }

  private fun presentModeText() {
    animateModeChange(
      inSet = drawButtonRow + setOf(drawSeekBar, textStyleToggle),
      outSet = allModeTools
    )
    animateInUndoTools()
  }

  private fun presentModeMoveSticker() {
    animateModeChange(
      inSet = drawButtonRow,
      outSet = allModeTools
    )
    animateInUndoTools()
  }

  private fun presentModeDelete() {
    animateModeChange(
      outSet = allModeTools
    )
    animateOutUndoTools()
  }

  private fun clearSelection() {
    selectableSet.forEach { it.isSelected = false }
  }

  private fun updateColorIndicator() {
    colorIndicator.drawable.colorFilter = SimpleColorFilter(drawSeekBar.getColor())
    colorIndicator.translationX = (drawSeekBar.thumb.bounds.left.toFloat() + ViewUtil.dpToPx(16))
    brushPreview.setColor(drawSeekBar.getColor())
    brushPreview.setBlur(currentMode == Mode.BLUR)
  }

  private fun animateModeChange(
    inSet: Set<View> = setOf(),
    outSet: Set<View> = setOf()
  ) {
    val actualOutSet = outSet - inSet
    val animations = animateInViewSet(inSet) + animateOutViewSet(actualOutSet)

    modeAnimatorSet = AnimatorSet().apply {
      playTogether(animations)
      duration = ANIMATION_DURATION
      interpolator = MediaAnimations.interpolator
      start()
    }
  }

  private fun animateUndoChange(
    inSet: Set<View> = setOf(),
    outSet: Set<View> = setOf()
  ) {
    val actualOutSet = outSet - inSet
    val animations = animateInViewSet(inSet) + animateOutViewSet(actualOutSet)

    undoAnimatorSet = AnimatorSet().apply {
      playTogether(animations)
      duration = ANIMATION_DURATION
      interpolator = MediaAnimations.interpolator
      start()
    }
  }

  private fun animateInViewSet(viewSet: Set<View>): List<Animator> {
    val fades = viewSet
      .map { child ->
        child.visible = true
        ObjectAnimator.ofFloat(child, "alpha", 1f)
      }

    val slides = viewSet.filter { it in viewsToSlide }.map { child ->
      ObjectAnimator.ofFloat(child, "translationY", 0f)
    }

    return fades + slides
  }

  private fun animateOutViewSet(viewSet: Set<View>): List<Animator> {
    val fades = viewSet.map { child ->
      ObjectAnimator.ofFloat(child, "alpha", 0f).apply {
        doOnEnd { child.visible = false }
      }
    }

    val slides = viewSet.filter { it in viewsToSlide }.map { child ->
      ObjectAnimator.ofFloat(child, "translationY", ViewUtil.dpToPx(56).toFloat())
    }

    return fades + slides
  }

  private fun animateInUndoTools() {
    animateUndoChange(
      inSet = undoToolsIfAvailable()
    )
  }

  private fun animateOutUndoTools() {
    animateUndoChange(
      outSet = undoTools
    )
  }

  private fun undoToolsIfAvailable(): Set<View> {
    return if (undoAvailability) {
      undoTools
    } else {
      setOf()
    }
  }

  enum class Mode {
    NONE,
    CROP,
    TEXT,
    DRAW,
    HIGHLIGHT,
    BLUR,
    MOVE_STICKER,
    MOVE_TEXT,
    DELETE,
    INSERT_STICKER
  }

  companion object {

    private const val ANIMATION_DURATION = 250L

    private val DRAW_WIDTH_BOUNDARIES: Map<Mode, Pair<Float, Float>> = mapOf(
      Mode.DRAW to ImageEditorValues.getMarkerWidthRange(),
      Mode.HIGHLIGHT to ImageEditorValues.getHighlighterWidthRange(),
      Mode.BLUR to ImageEditorValues.getBlurWidthRange()
    )

    private fun withCustomAlpha(color: Int, alpha: Int = 225): Int {
      // 确保 alpha 值在 0 到 255 的范围内
      val clampedAlpha = alpha.coerceIn(0, 255)
      // 将 alpha 值转换为十六进制字符串，并确保是两位数 (例如，10 变为 "0A")
      val alphaHex = clampedAlpha.toString(16).padStart(2, '0').uppercase(Locale.getDefault())
      // 构建新的颜色值，将传入颜色的 RGB 分量与新的 alpha 值合并
      val newColor = (alphaHex.toInt(16) shl 24) or (color and 0x00FFFFFF)
      return newColor
    }
  }

  interface EventListener {
    fun onModeStarted(mode: Mode, previousMode: Mode)
    fun onColorChange(color: Int)
    fun onBrushWidthChange()
    fun onBlurFacesToggled(enabled: Boolean)
    fun onUndo()
    fun onClearAll()
    fun onDelete()
    fun onSave()
    fun onFlipHorizontal()
    fun onRotate90AntiClockwise()
    fun onCropAspectLock()
    fun onTextStyleToggle()
    fun onDialRotationGestureStarted()
    fun onDialRotationChanged(degrees: Float)
    fun onDialRotationGestureFinished()
    val isCropAspectLocked: Boolean

    fun onRequestFullScreen(fullScreen: Boolean, hideKeyboard: Boolean)
    fun onDone()
    fun onCancel()
  }
}
