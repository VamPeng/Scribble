package com.demo.scribbledemo.media

import android.animation.Animator
import android.animation.AnimatorSet
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.demo.scribbledemo.R
import kotlinx.coroutines.launch

/**
 * 2025/5/15 13:51
 *
 * author: yuhuipeng
 *
 * description:
 */
class MediaReviewFragment : Fragment(R.layout.media_review_fragment) {

  private val sharedViewModel: MediaSelectionViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private lateinit var drawToolButton: View
  private lateinit var cropAndRotateButton: View
  private lateinit var qualityButton: ImageView
  private lateinit var saveButton: View
  private lateinit var pager: ViewPager2
  private lateinit var controls: ConstraintLayout
  private lateinit var controlsShade: View

  private var animatorSet: AnimatorSet? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    drawToolButton = view.findViewById(R.id.draw_tool)
    cropAndRotateButton = view.findViewById(R.id.crop_and_rotate_tool)
    qualityButton = view.findViewById(R.id.quality_selector)
    saveButton = view.findViewById(R.id.save_to_media)
    pager = view.findViewById(R.id.media_pager)
    controls = view.findViewById(R.id.controls)
    controlsShade = view.findViewById(R.id.controls_shade)

    val pagerAdapter = MediaReviewFragmentPagerAdapter(this)

//        disposables += sharedViewModel.hudCommands.subscribe {
//            when (it) {
//                HudCommand.ResumeEntryTransition -> startPostponedEnterTransition()
//                else -> Unit
//            }
//        }

    pager.adapter = pagerAdapter

    drawToolButton.setOnClickListener {
      sharedViewModel.sendCommand(HudCommand.StartDraw)
    }

    cropAndRotateButton.setOnClickListener {
      sharedViewModel.sendCommand(HudCommand.StartCropAndRotate)
    }

    qualityButton.setOnClickListener {
//            QualitySelectorBottomSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }

    saveButton.setOnClickListener {
      sharedViewModel.sendCommand(HudCommand.SaveMedia)
    }

    pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        qualityButton.alpha = 0f
        saveButton.alpha = 0f
        sharedViewModel.onPageChanged(position)
      }
    })


    viewLifecycleOwner.lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        sharedViewModel.state.collect { state ->
          pagerAdapter.submitMedia(state.selectedMedia)
          presentPager(state)
          presentImageQualityToggle(state)
          computeViewStateAndAnimate(state)
        }
      }
    }
  }

  private fun presentPager(state: MediaSelectionState) {
    pager.isUserInputEnabled = state.isTouchEnabled

    val indexOfSelectedItem = state.selectedMedia.indexOf(state.focusedMedia)

    if (pager.currentItem == indexOfSelectedItem) {
      return
    }

    if (indexOfSelectedItem != -1) {
      pager.setCurrentItem(indexOfSelectedItem, false)
    } else {
      pager.setCurrentItem(0, false)
    }
  }

  private fun presentImageQualityToggle(state: MediaSelectionState) {
//        qualityButton.updateLayoutParams<ConstraintLayout.LayoutParams> {
//            if (MediaUtil.isImageAndNotGif(state.focusedMedia?.contentType ?: "")) {
//                startToStart = ConstraintLayout.LayoutParams.UNSET
//                startToEnd = cropAndRotateButton.id
//            } else {
//                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
//                startToEnd = ConstraintLayout.LayoutParams.UNSET
//            }
//        }
    qualityButton.setImageResource(
      R.drawable.symbol_quality_high_slash_24
    )
  }

  private fun computeViewStateAndAnimate(state: MediaSelectionState) {
    this.animatorSet?.cancel()

    val animators = mutableListOf<Animator>()

    animators.addAll(computeSaveButtonAnimators(state))
    animators.addAll(computeQualityButtonAnimators(state))
    animators.addAll(computeCropAndRotateButtonAnimators(state))
    animators.addAll(computeDrawToolButtonAnimators(state))
    animators.addAll(computeControlsShadeAnimators(state))

    val animatorSet = AnimatorSet()
    animatorSet.playTogether(animators)
    animatorSet.start()

    this.animatorSet = animatorSet
  }

  private fun computeControlsShadeAnimators(state: MediaSelectionState): List<Animator> {
    val animators = mutableListOf<Animator>()
    animators += if (state.isTouchEnabled) {
      MediaReviewAnimatorController.getFadeInAnimator(controlsShade)
    } else {
      MediaReviewAnimatorController.getFadeOutAnimator(controlsShade)
    }
    return animators
  }

  private fun computeSaveButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled) {
      listOf(
        MediaReviewAnimatorController.getFadeInAnimator(saveButton)
      )
    } else {
      listOf(
        MediaReviewAnimatorController.getFadeOutAnimator(saveButton)
      )
    }
  }

  private fun computeQualityButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(qualityButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(qualityButton))
    }
  }

  private fun computeCropAndRotateButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(cropAndRotateButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(cropAndRotateButton))
    }
  }

  private fun computeDrawToolButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(drawToolButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(drawToolButton))
    }
  }

  companion object {
    fun newInstance(): MediaReviewFragment {
      val fragment = MediaReviewFragment()
      return fragment
    }
  }

}