package com.demo.scribbledemo.scribble

import android.content.DialogInterface
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.demo.scribbledemo.util.FontTypefaceProvider
import com.demo.scribbledemo.R
import com.demo.scribbledemo.renderers.UriGlideRenderer
import com.demo.scribbledemo.animation.MediaAnimations
import com.demo.scribbledemo.animation.ResizeAnimation
import com.demo.scribbledemo.keyboardentries.TextEntryDialogFragment
import com.demo.scribbledemo.util.Permissions
import com.demo.scribbledemo.util.ThrottledDebouncer
import com.demo.scribbledemo.util.ViewUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.imageeditor.core.ColorableRenderer
import org.signal.imageeditor.core.ImageEditorView
import org.signal.imageeditor.core.ImageEditorView.TapListener
import org.signal.imageeditor.core.SelectableRenderer
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.BezierDrawingRenderer
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer
import kotlin.math.max
import kotlin.math.min


/**
 * 2025/5/15 09:43
 *
 * author: yuhuipeng
 *
 * description:
 */
open class ImageEditorFragment : Fragment(), ImageEditorHudV2.EventListener,
  TextEntryDialogFragment.Controller {

  val TAG = this::class.simpleName

  private val imageEditorView by lazy { view?.findViewById<ImageEditorView>(R.id.image_editor_view)!! }
  private val imageEditorHud by lazy { view?.findViewById<ImageEditorHudV2>(R.id.scribble_hud)!! }
  private val imageUri by lazy {
    requireNotNull(
      if (Build.VERSION.SDK_INT >= 33) {
        requireArguments().getParcelable(KEY_IMAGE_URI, Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        (requireArguments().getParcelable(KEY_IMAGE_URI))
      }
    )
  }
  private var hasMadeAnEditThisSession = false
  private var wasInTrashHitZone = false

  private var currentSelection: EditorElement? = null
  private var imageMaxHeight = 0
  private var imageMaxWidth = 0

  private val deleteFadeDebouncer: ThrottledDebouncer = ThrottledDebouncer(500)
  private var initialDialImageDegrees = 0f
  private var initialDialScale = 0f
  private var minDialScaleDown = 0f

  private var restoredModel: EditorModel? = null

  private lateinit var controller: Controller

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val parent = parentFragment
    controller = if (parent is Controller) {
      parent
    } else if (activity is Controller) {
      activity as Controller
    } else {
      throw IllegalStateException("Parent must implement Controller interface.")
    }

    imageMaxWidth = 4096
    imageMaxHeight = 4096
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.editor_fragment, container, false)
  }

  private fun changeEntityColor(selectedColor: Int) {
    if (currentSelection != null) {
      val renderer = currentSelection!!.renderer
      if (renderer is ColorableRenderer) {
        renderer.color = selectedColor
        onDrawingChanged(stillTouching = false, isUserEdit = true)
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    showSelectedMedia(imageUri)
  }

  private fun showSelectedMedia(uri: Uri) {
    imageEditorView.setTypefaceProvider(FontTypefaceProvider())
    val width = resources.displayMetrics.widthPixels
    val height: Int = ((VERTICAL_RATIO / HORIZONTAL_RATIO) * width).toInt() - CONTROLS_PROTECTION
    imageEditorView.minimumHeight = height
    imageEditorView.requestLayout()
    val marginBottom = resources.displayMetrics.heightPixels - height
    imageEditorHud.setBottomOfImageEditorView(marginBottom)
    imageEditorHud.setEventListener(this)
    imageEditorView.setDragListener(dragListener)
    imageEditorView.setTapListener(selectionListener)
    imageEditorView.setDrawingChangedListener { stillTouching: Boolean ->
      onDrawingChanged(stillTouching, true)
    }
    imageEditorView.setUndoRedoStackListener(::onUndoRedoAvailabilityChanged)

    @ColorInt val blackoutColor = ContextCompat.getColor(
      requireContext(),
      R.color.signal_colorBackground
    )
    var editorModel: EditorModel? = null
    if (restoredModel != null) {
      editorModel = restoredModel
      restoredModel = null
    }
    val image = EditorElement(
      UriGlideRenderer(
        uri,
        true,
        imageMaxWidth,
        imageMaxHeight,
        UriGlideRenderer.STRONG_BLUR,
        mainImageRequestListener
      )
    )
    image.flags.setSelectable(false).persist()
    editorModel = editorModel ?: EditorModel.create(blackoutColor)
    editorModel!!.addElement(image)

    imageEditorView.model = editorModel

    onDrawingChanged(stillTouching = false, isUserEdit = false)
  }


  fun setMode(mode: ImageEditorHudV2.Mode) {
    val currentMode: ImageEditorHudV2.Mode = imageEditorHud.getMode()
    if (currentMode === mode) {
      return
    }
    imageEditorHud.setMode(mode)
  }

  private fun onDrawingChanged(stillTouching: Boolean, isUserEdit: Boolean) {
    if (isUserEdit) {
      hasMadeAnEditThisSession = true
    }
  }

  private fun onUndoRedoAvailabilityChanged(undoAvailable: Boolean, redoAvailable: Boolean) {
    imageEditorHud.setUndoAvailability(undoAvailable)
  }

  private val mainImageRequestListener: RequestListener<Bitmap> = object :
    RequestListener<Bitmap> {

    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Bitmap>,
      isFirstResource: Boolean
    ): Boolean {
      Log.d(TAG, "image load failed")
//            controller.onMainImageFailedToLoad()
      return false
    }

    override fun onResourceReady(
      resource: Bitmap,
      model: Any,
      target: Target<Bitmap>,
      dataSource: DataSource,
      isFirstResource: Boolean
    ): Boolean {
      Log.d(TAG, "image load ready")
//            controller.onMainImageLoaded()
      return false
    }

  }

  private fun updateViewPortScaling(
    mode: ImageEditorHudV2.Mode,
    previousMode: ImageEditorHudV2.Mode,
    orientation: Int,
    force: Boolean
  ) {
    val shouldScaleViewPortForCurrentMode: Boolean = shouldScaleViewPort(mode)
    val shouldScaleViewPortForPreviousMode: Boolean = shouldScaleViewPort(previousMode)
    val hudProtection: Int = getHudProtection(mode)

    if (shouldScaleViewPortForCurrentMode != shouldScaleViewPortForPreviousMode || force) {
      if (shouldScaleViewPortForCurrentMode) {
        scaleViewPortForDrawing(orientation, hudProtection)
      } else {
        restoreViewPortScaling(orientation)
      }
    }
  }

  private fun getHudProtection(mode: ImageEditorHudV2.Mode): Int {
    return if (mode === ImageEditorHudV2.Mode.CROP) {
      CROP_HUD_PROTECTION
    } else {
      DRAW_HUD_PROTECTION
    }
  }

  private fun startTextEntityEditing(textElement: EditorElement, selectAll: Boolean) {
    imageEditorView.startTextEditing(textElement)

    TextEntryDialogFragment.show(
      childFragmentManager,
      textElement,
      false,
      selectAll,
      imageEditorHud.getColorIndex()
    )
  }

  private fun updateHudDialRotation() {
    imageEditorHud.setDialRotation(getRotationDegreesRounded(imageEditorView.model.mainImage))
    initialDialScale = imageEditorView.model.mainImage!!.localScaleX
  }

  private fun shouldHandleOnBackPressed(mode: ImageEditorHudV2.Mode): Boolean {
    return mode === ImageEditorHudV2.Mode.CROP || mode === ImageEditorHudV2.Mode.DRAW || mode === ImageEditorHudV2.Mode.HIGHLIGHT || mode === ImageEditorHudV2.Mode.BLUR || mode === ImageEditorHudV2.Mode.TEXT || mode === ImageEditorHudV2.Mode.MOVE_STICKER || mode === ImageEditorHudV2.Mode.MOVE_TEXT || mode === ImageEditorHudV2.Mode.INSERT_STICKER
  }

  protected fun addText() {
    val initialText = ""
    val color = imageEditorHud.getActiveColor()
    val renderer = MultiLineTextRenderer(initialText, color, MultiLineTextRenderer.Mode.REGULAR)
    val element = EditorElement(renderer, EditorModel.Z_TEXT)

    imageEditorView.model.addElementCentered(element, 1f)
    imageEditorView.invalidate()

    setCurrentSelection(element)

    startTextEntityEditing(element, true)
  }

  private var resizeAnimation: ResizeAnimation? = null
  private fun scaleViewPortForDrawing(orientation: Int, protection: Int) {
    resizeAnimation?.cancel()

    val aspectRatio: Float = getAspectRatioForOrientation(orientation)
    var targetWidth: Int = getWidthForOrientation(orientation) - ViewUtil.dpToPx(32)
    var targetHeight = ((1 / aspectRatio) * targetWidth).toInt()
    val maxHeight: Int = getHeightForOrientation(orientation) - protection

    if (targetHeight > maxHeight) {
      targetHeight = maxHeight
      targetWidth = Math.round(targetHeight * aspectRatio)
    }

    resizeAnimation = ResizeAnimation(imageEditorView, targetWidth, targetHeight)
    resizeAnimation?.setDuration(250)
    resizeAnimation?.interpolator = MediaAnimations.interpolator
    imageEditorView.startAnimation(resizeAnimation)
  }

  private fun restoreViewPortScaling(orientation: Int) {
    resizeAnimation?.cancel()

    val maxHeight: Int = getHeightForOrientation(orientation)
    val aspectRatio: Float = getAspectRatioForOrientation(orientation)
    var targetWidth: Int = getWidthForOrientation(orientation)
    var targetHeight = ((1 / aspectRatio) * targetWidth).toInt() - CONTROLS_PROTECTION

    if (targetHeight > maxHeight) {
      targetHeight = maxHeight
      targetWidth = Math.round(targetHeight * aspectRatio)
    }

    resizeAnimation = ResizeAnimation(imageEditorView, targetWidth, targetHeight)
    resizeAnimation?.setDuration(250)
    resizeAnimation?.interpolator = MediaAnimations.interpolator
    imageEditorView.startAnimation(resizeAnimation)
  }

  private fun getHeightForOrientation(orientation: Int): Int {
    return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      max(
        resources.displayMetrics.heightPixels.toDouble(),
        resources.displayMetrics.widthPixels.toDouble()
      ).toInt()
    } else {
      min(
        resources.displayMetrics.heightPixels.toDouble(),
        resources.displayMetrics.widthPixels.toDouble()
      ).toInt()
    }
  }

  private fun getWidthForOrientation(orientation: Int): Int {
    return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      min(
        resources.displayMetrics.heightPixels.toDouble(),
        resources.displayMetrics.widthPixels.toDouble()
      ).toInt()
    } else {
      max(
        resources.displayMetrics.heightPixels.toDouble(),
        resources.displayMetrics.widthPixels.toDouble()
      ).toInt()
    }
  }

  private fun getAspectRatioForOrientation(orientation: Int): Float {
    return if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      PORTRAIT_ASPECT_RATIO
    } else {
      1f / PORTRAIT_ASPECT_RATIO
    }
  }

  private fun getRotationDegreesRounded(editorElement: EditorElement?): Float {
    if (editorElement == null) {
      return 0f
    }
    return Math.round(Math.toDegrees(editorElement.localRotationAngle.toDouble()))
      .toFloat()
  }

  override fun onModeStarted(mode: ImageEditorHudV2.Mode, previousMode: ImageEditorHudV2.Mode) {
    onBackPressedCallback.isEnabled = shouldHandleOnBackPressed(mode)

    imageEditorView.mode = ImageEditorView.Mode.MoveAndResize
    imageEditorView.doneTextEditing()

    controller.onTouchEventsNeeded(mode !== ImageEditorHudV2.Mode.NONE)

    updateViewPortScaling(mode, previousMode, resources.configuration.orientation, false)

    if (mode !== ImageEditorHudV2.Mode.CROP) {
      imageEditorView.model.doneCrop()
    }

    imageEditorView.model
      .trash
      .flags
      .setVisible(mode === ImageEditorHudV2.Mode.DELETE)
      .persist()

    updateHudDialRotation()

    when (mode) {
      ImageEditorHudV2.Mode.CROP -> {
        imageEditorView.model.startCrop()
      }

      ImageEditorHudV2.Mode.DRAW, ImageEditorHudV2.Mode.HIGHLIGHT -> {
        onBrushWidthChange()
      }

      ImageEditorHudV2.Mode.BLUR -> {
        onBrushWidthChange()
        imageEditorHud.setBlurFacesToggleEnabled(imageEditorView.model.hasFaceRenderer())
      }

      ImageEditorHudV2.Mode.TEXT -> {
        addText()
      }

      ImageEditorHudV2.Mode.NONE -> {
        setCurrentSelection(null)
        hasMadeAnEditThisSession = false
      }

      else -> Unit
    }
  }

  private val dragListener: ImageEditorView.DragListener = object : ImageEditorView.DragListener {
    override fun onDragStarted(editorElement: EditorElement?) {
      if (imageEditorHud.getMode() === ImageEditorHudV2.Mode.CROP) {
        updateHudDialRotation()
        return
      }

      if (editorElement == null || editorElement.renderer is BezierDrawingRenderer) {
        setCurrentSelection(null)
      } else {
        setCurrentSelection(editorElement)
      }

      if (imageEditorView.mode == ImageEditorView.Mode.MoveAndResize) {
        imageEditorHud.setMode(ImageEditorHudV2.Mode.DELETE)
      } else {
        imageEditorHud.animate().alpha(0f)
      }
    }

    override fun onDragMoved(editorElement: EditorElement?, isInTrashHitZone: Boolean) {
      if (imageEditorHud.getMode() === ImageEditorHudV2.Mode.CROP || editorElement == null) {
        updateHudDialRotation()
        return
      }

      if (isInTrashHitZone) {
        deleteFadeDebouncer.publish {
          if (!wasInTrashHitZone) {
            wasInTrashHitZone = true
            if (imageEditorHud.isHapticFeedbackEnabled) {
              imageEditorHud.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
          }
          editorElement.animatePartialFadeOut(Runnable { imageEditorView.invalidate() })
        }
      } else {
        deleteFadeDebouncer.publish {
          wasInTrashHitZone = false
          editorElement.animatePartialFadeIn(Runnable { imageEditorView.invalidate() })
        }
      }
    }

    override fun onDragEnded(editorElement: EditorElement?, isInTrashHitZone: Boolean) {
      wasInTrashHitZone = false
      imageEditorHud.animate().alpha(1f)
      if (imageEditorHud.getMode() === ImageEditorHudV2.Mode.CROP) {
        updateHudDialRotation()
        return
      }

      if (isInTrashHitZone) {
        deleteFadeDebouncer.clear()
        onDelete()
        setCurrentSelection(null)
        onPopEditorMode()
      } else if (editorElement != null && editorElement.renderer is MultiLineTextRenderer) {
        editorElement.animatePartialFadeIn(Runnable { imageEditorView.invalidate() })

        if (imageEditorHud.getMode() !== ImageEditorHudV2.Mode.TEXT) {
          imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_TEXT)
        }
      } else if (editorElement != null && editorElement.renderer is UriGlideRenderer) {
        editorElement.animatePartialFadeIn(Runnable { imageEditorView.invalidate() })
        imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_STICKER)
      }
    }
  }

  private val selectionListener: TapListener = object : TapListener {
    override fun onEntityDown(editorElement: EditorElement?) {
      if (editorElement != null) {
        controller.onTouchEventsNeeded(true)
      }
    }

    override fun onEntitySingleTap(editorElement: EditorElement?) {
      setCurrentSelection(editorElement)
      if (currentSelection != null) {
        if (editorElement!!.renderer is MultiLineTextRenderer) {
          setTextElement(
            editorElement,
            editorElement.renderer as ColorableRenderer,
            imageEditorView.isTextEditing
          )
        } else {
          // tappable render sticker
        }
      } else {
        onPopEditorMode()
      }
    }

    override fun onEntityDoubleTap(editorElement: EditorElement) {
      setCurrentSelection(editorElement)
      if (editorElement.renderer is MultiLineTextRenderer) {
        setTextElement(editorElement, editorElement.renderer as ColorableRenderer, true)
      }
    }

    private fun setTextElement(
      editorElement: EditorElement,
      colorableRenderer: ColorableRenderer,
      startEditing: Boolean
    ) {
      val color = colorableRenderer.color
      imageEditorHud.enterMode(ImageEditorHudV2.Mode.TEXT)
      imageEditorHud.setActiveColor(color)
      if (startEditing) {
        startTextEntityEditing(editorElement, false)
      }
    }
  }

  override fun onColorChange(color: Int) {
    imageEditorView.setDrawingBrushColor(color)
    changeEntityColor(color)
  }

  override fun onBrushWidthChange() {
    val mode = imageEditorHud.getMode()
    imageEditorView.startDrawing(
      imageEditorHud.getActiveBrushWidth(),
      if (mode === ImageEditorHudV2.Mode.HIGHLIGHT) Paint.Cap.SQUARE else Paint.Cap.ROUND,
      mode === ImageEditorHudV2.Mode.BLUR
    )
  }

  override fun onBlurFacesToggled(enabled: Boolean) {
    val model = imageEditorView.model
    val mainImage = model.mainImage
    if (mainImage == null) {
      imageEditorHud.hideBlurToast()
      return
    }
    if (!enabled) {
      model.clearFaceRenderers()
      imageEditorHud.hideBlurToast()
      return
    }
  }

  override fun onUndo() {
    imageEditorView.model.undo()
    imageEditorHud.setBlurFacesToggleEnabled(imageEditorView.model.hasFaceRenderer())
    updateHudDialRotation()
  }

  override fun onClearAll() {
    imageEditorView.model.clearUndoStack()
    updateHudDialRotation()
  }

  override fun onDelete() {
    imageEditorView.deleteElement(currentSelection)
  }

  override fun onSave() {
    Permissions.hasAll(requireContext())
    Toast.makeText(requireContext(), "save", Toast.LENGTH_SHORT).show()
  }

  override fun onFlipHorizontal() {
    imageEditorView.model.flipHorizontal()
  }

  override fun onRotate90AntiClockwise() {
    imageEditorView.model.rotate90anticlockwise()
  }

  override fun onCropAspectLock() {
    imageEditorView.model.setCropAspectLock(!imageEditorView.model.isCropAspectLocked)
  }

  override fun onTextStyleToggle() {
    if (currentSelection != null && currentSelection!!.renderer is MultiLineTextRenderer) {
      (currentSelection!!.renderer as MultiLineTextRenderer).nextMode()
    }
  }

  override fun onDialRotationGestureStarted() {
    val localScaleX = imageEditorView.model.mainImage!!.localScaleX
    minDialScaleDown = initialDialScale / localScaleX
    imageEditorView.model.pushUndoPoint()
    imageEditorView.model.updateUndoRedoAvailabilityState()
    initialDialImageDegrees = Math.toDegrees(imageEditorView.model.mainImage!!.localRotationAngle.toDouble())
      .toFloat()
  }

  override fun onDialRotationChanged(degrees: Float) {
    imageEditorView.setMainImageEditorMatrixRotation(
      degrees - initialDialImageDegrees,
      minDialScaleDown
    )
  }

  override fun onDialRotationGestureFinished() {
    imageEditorView.model.mainImage!!.commitEditorMatrix()
    imageEditorView.model.postEdit(true)
    imageEditorView.invalidate()
  }

  override val isCropAspectLocked: Boolean
    get() = imageEditorView.model.isCropAspectLocked

  override fun onRequestFullScreen(fullScreen: Boolean, hideKeyboard: Boolean) {
    controller.onRequestFullScreen(fullScreen, hideKeyboard)
  }

  override fun onDone() {
    controller.onDoneEditing()
  }

  override fun onCancel() {
    if (hasMadeAnEditThisSession) {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.MediaReviewImagePageFragment__discard_changes)
        .setMessage(R.string.MediaReviewImagePageFragment__youll_lose_any_changes)
        .setPositiveButton(R.string.MediaReviewImagePageFragment__discard) { d, w ->
          d.dismiss()
          imageEditorHud.setMode(ImageEditorHudV2.Mode.NONE)
          controller.onCancelEditing()
        }
        .setNegativeButton(
          android.R.string.cancel
        ) { d: DialogInterface, w: Int -> d.dismiss() }
        .show()
    } else {
      imageEditorHud.setMode(ImageEditorHudV2.Mode.NONE)
      controller.onCancelEditing()
    }
  }

  private fun onPopEditorMode() {
    setCurrentSelection(null)

    when (imageEditorHud.getMode()) {
      ImageEditorHudV2.Mode.NONE -> return

      ImageEditorHudV2.Mode.CROP,
      ImageEditorHudV2.Mode.DRAW,
      ImageEditorHudV2.Mode.HIGHLIGHT,
      ImageEditorHudV2.Mode.BLUR -> onCancel()

      ImageEditorHudV2.Mode.INSERT_STICKER,
      ImageEditorHudV2.Mode.MOVE_STICKER,
      ImageEditorHudV2.Mode.MOVE_TEXT,
      ImageEditorHudV2.Mode.DELETE,
      ImageEditorHudV2.Mode.TEXT -> {
        controller.onTouchEventsNeeded(true)
        imageEditorHud.setMode(ImageEditorHudV2.Mode.DRAW)
      }
    }
  }

  private fun setCurrentSelection(currentSelection: EditorElement?) {
    setSelectionState(this.currentSelection, false)

    this.currentSelection = currentSelection

    setSelectionState(this.currentSelection, true)

    imageEditorView.invalidate()
  }

  private fun setSelectionState(editorElement: EditorElement?, selected: Boolean) {
    if (editorElement != null && editorElement.renderer is SelectableRenderer) {
      (editorElement.renderer as SelectableRenderer).onSelected(selected)
    }
    imageEditorView.model.setSelected(if (selected) editorElement else null)
  }

  private val onBackPressedCallback: OnBackPressedCallback = object :
    OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      onPopEditorMode()
    }
  }

  interface Controller {
    fun onTouchEventsNeeded(needed: Boolean)

    fun onRequestFullScreen(fullScreen: Boolean, hideKeyboard: Boolean) = Unit

    fun onDoneEditing()

    fun onCancelEditing()

    fun onMainImageLoaded() = Unit

    fun onMainImageFailedToLoad() = Unit

    fun restoreState()
  }

  override fun onTextEntryDialogDismissed(hasText: Boolean) {
    imageEditorView.doneTextEditing()
    if (hasText) {
      imageEditorHud.setMode(ImageEditorHudV2.Mode.MOVE_TEXT)
    } else {
      onUndo()
      imageEditorHud.setMode(ImageEditorHudV2.Mode.DRAW)
    }
  }

  override fun zoomToFitText(editorElement: EditorElement, textRenderer: MultiLineTextRenderer) {
    imageEditorView.zoomToFitText(editorElement, textRenderer)
  }

  override fun onTextColorChange(colorIndex: Int) {
    imageEditorHud.setColorIndex(colorIndex)
    onColorChange(imageEditorHud.getActiveColor())
  }

  fun saveState(): Any {
    val data = Data()
    data.writeModel(imageEditorView.model)
    return data
  }

  fun restoreState(state: Any) {
    if (state is Data) {
      val data: Data = state
      val model: EditorModel? = data.readModel()
      if (model != null) {
        imageEditorView.model = model
        onDrawingChanged(stillTouching = false, isUserEdit = false)
      }
    } else {
      Log.w(
        "ImageEditorFragment",
        "Received a bad saved state. Received class: " + state.javaClass.name
      )
    }
  }

  companion object {
    private const val KEY_IMAGE_URI = "image_uri"
    private const val HORIZONTAL_RATIO = 9f
    private const val VERTICAL_RATIO = 16f
    private const val PORTRAIT_ASPECT_RATIO: Float = HORIZONTAL_RATIO / VERTICAL_RATIO

    val DRAW_HUD_PROTECTION: Int = dpToPx(72)
    val CROP_HUD_PROTECTION: Int = dpToPx(144)
    val CONTROLS_PROTECTION: Int = dpToPx(74)
    private fun dpToPx(dp: Int): Int {
      return Math.round(dp * Resources.getSystem().displayMetrics.density)
    }

    fun newInstance(imageUri: Uri): ImageEditorFragment {
      val bundle = Bundle()
      bundle.putParcelable(KEY_IMAGE_URI, imageUri)
      val fragment = ImageEditorFragment()
      fragment.arguments = bundle
      return fragment
    }

    private fun shouldScaleViewPort(mode: ImageEditorHudV2.Mode): Boolean {
      return mode !== ImageEditorHudV2.Mode.NONE
    }

    fun serialize(parceable: Parcelable): ByteArray {
      val parcel = Parcel.obtain()
      parceable.writeToParcel(parcel, 0)
      val bytes = parcel.marshall()
      parcel.recycle()
      return bytes
    }

    fun deserialize(bytes: ByteArray): Parcel {
      val parcel = Parcel.obtain()
      parcel.unmarshall(bytes, 0, bytes.size)
      parcel.setDataPosition(0)
      return parcel
    }

    fun <T> deserialize(bytes: ByteArray, creator: Parcelable.Creator<T>): T {
      val parcel = deserialize(bytes)
      return creator.createFromParcel(parcel)
    }
  }

  class Data @JvmOverloads constructor(val bundle: Bundle = Bundle()) {
    fun writeModel(model: EditorModel) {
      val bytes: ByteArray = serialize(model)
      bundle.putByteArray("MODEL", bytes)
    }

    fun readModel(): EditorModel? {
      val bytes = bundle.getByteArray("MODEL") ?: return null
      return deserialize(bytes, EditorModel.CREATOR)
    }
  }

}