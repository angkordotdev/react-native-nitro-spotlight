package com.margelo.nitro.spotlight

import android.graphics.Color
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.Keep
import com.facebook.common.internal.DoNotStrip
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.ThemedReactContext
import com.margelo.nitro.views.RecyclableView

@DoNotStrip
@Keep
class HybridSpotlightView(
  private val context: ThemedReactContext,
) : HybridSpotlightViewSpec(), RecyclableView {

  // -------------------------------------------------------------------------
  // Views
  // -------------------------------------------------------------------------

  /**
   * The dim + cutout overlay. Returned as the Fabric/Nitro view so React
   * Native sizes it (via StyleSheet.absoluteFillObject in the JS wrapper) and
   * JS siblings (SpotlightTooltip) render above it in Android z-order —
   * matching the iOS architecture exactly.
   */
  private val spotlightView = SpotlightOverlayView(context)

  /**
   * Covers the area above the React tree (status bar + native navigation
   * header) so the dim feels full-screen. Added to the decor-view only while
   * a spotlight is active; removed on clear(). The screen-body overlay with
   * the hole stays in the React tree so tooltips can render above it.
   */
  private val headerDimView = View(context)
  private var headerDimAdded = false
  private var decorView: ViewGroup? = null

  /**
   * Last requested highlight rect in measureInWindow DIP, or null while no
   * spotlight is active. Source of truth for "is a highlight showing", used to
   * re-emit onTargetLayout after layout changes and to gate the header dim.
   */
  private var lastWindowRect: RectF? = null

  init {
    // Clean up headerDimView when the spotlight overlay detaches from its
    // window (unmount without recycle). prepareForRecycle() handles the pool
    // case; this listener covers destruction / direct unmount. On re-attach
    // (Teleport reparent, react-native-screens) restore it if a highlight is
    // still active.
    spotlightView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) {
        if (lastWindowRect != null) showHeaderDim()
      }

      override fun onViewDetachedFromWindow(v: View) {
        hideHeaderDim()
      }
    })

    // First layout / rotation / reparent: the overlay origin may have changed,
    // so re-emit the corrected target rect and (re)size the header dim, which
    // depends on the overlay's laid-out screen position.
    spotlightView.onGeometryChanged = {
      emitTargetLayout()
      showHeaderDim()
    }
  }

  // -------------------------------------------------------------------------
  // Property backing fields
  // -------------------------------------------------------------------------

  private var dimOpacityValue: Double? = null
  private var shapeValue: String? = null
  private var borderRadiusValue: Double? = null
  private var paddingValue: Double? = null
  private var borderWidthValue: Double? = null
  private var borderColorValue: String? = null
  private var allowOverlayClickValue: Boolean? = null
  private var enteringAnimationValue: String? = null
  private var exitAnimationValue: String? = null
  private var exitDurationMsValue: Double? = null

  // -------------------------------------------------------------------------
  // Nitro / Fabric entry-point
  // -------------------------------------------------------------------------

  override val view: View get() = spotlightView

  // -------------------------------------------------------------------------
  // Properties
  // -------------------------------------------------------------------------

  override var dimOpacity: Double?
    get() = dimOpacityValue
    set(value) {
      if (dimOpacityValue == value) return
      dimOpacityValue = value
      UiThreadUtil.runOnUiThread {
        spotlightView.dimOpacity = (value ?: DEFAULT_DIM_OPACITY).toFloat()
        if (headerDimAdded) {
          headerDimView.setBackgroundColor(dimArgb(value ?: DEFAULT_DIM_OPACITY))
        }
      }
    }

  override var shape: String?
    get() = shapeValue
    set(value) {
      if (shapeValue == value) return
      shapeValue = value
      UiThreadUtil.runOnUiThread { spotlightView.shape = value ?: DEFAULT_SHAPE }
    }

  override var cornerRadius: Double?
    get() = borderRadiusValue
    set(value) {
      if (borderRadiusValue == value) return
      borderRadiusValue = value
      UiThreadUtil.runOnUiThread { spotlightView.borderRadius = (value ?: DEFAULT_BORDER_RADIUS).toFloat() }
    }

  override var cutoutPadding: Double?
    get() = paddingValue
    set(value) {
      if (paddingValue == value) return
      paddingValue = value
      UiThreadUtil.runOnUiThread { spotlightView.padding = (value ?: DEFAULT_PADDING).toFloat() }
    }

  override var ringWidth: Double?
    get() = borderWidthValue
    set(value) {
      if (borderWidthValue == value) return
      borderWidthValue = value
      UiThreadUtil.runOnUiThread { spotlightView.borderWidth = (value ?: DEFAULT_BORDER_WIDTH).toFloat() }
    }

  override var ringColor: String?
    get() = borderColorValue
    set(value) {
      if (borderColorValue == value) return
      borderColorValue = value
      UiThreadUtil.runOnUiThread { spotlightView.borderColor = value ?: DEFAULT_BORDER_COLOR }
    }

  override var allowOverlayClick: Boolean?
    get() = allowOverlayClickValue
    set(value) {
      if (allowOverlayClickValue == value) return
      allowOverlayClickValue = value
      UiThreadUtil.runOnUiThread {
        spotlightView.allowOverlayClick = value ?: DEFAULT_ALLOW_OVERLAY_CLICK
        syncHeaderDimTouch()
      }
    }

  override var enteringAnimation: String?
    get() = enteringAnimationValue
    set(value) {
      if (enteringAnimationValue == value) return
      enteringAnimationValue = value
      UiThreadUtil.runOnUiThread {
        spotlightView.enteringAnimation = value ?: TRANSITION_ZOOM
      }
    }

  override var exitAnimation: String?
    get() = exitAnimationValue
    set(value) {
      if (exitAnimationValue == value) return
      exitAnimationValue = value
      UiThreadUtil.runOnUiThread {
        spotlightView.exitAnimation = value ?: TRANSITION_ZOOM
      }
    }

  override var exitDurationMs: Double?
    get() = exitDurationMsValue
    set(value) {
      exitDurationMsValue = value
    }

  override var onTargetLayout: ((com.margelo.nitro.spotlight.Rect) -> Unit)? = null

  override var onBackdropPress: (() -> Unit)? = null
    set(value) {
      field = value
      UiThreadUtil.runOnUiThread {
        spotlightView.onBackdropPress = value
      }
    }

  // -------------------------------------------------------------------------
  // Commands
  // -------------------------------------------------------------------------

  override fun highlight(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
  ) {
    // Mirror iOS: an unmeasured/zero-size target is ignored rather than
    // clearing the overlay or dimming the header with no hole.
    if (width <= 0.0 || height <= 0.0) return
    UiThreadUtil.runOnUiThread {
      val entering = lastWindowRect == null
      lastWindowRect = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
      spotlightView.setHighlight(
        xDp      = x.toFloat(),
        yDp      = y.toFloat(),
        widthDp  = width.toFloat(),
        heightDp = height.toFloat(),
        animated = false,
      )
      emitTargetLayout()
      showHeaderDim()
      animateHeaderDimIn(entering, durationMs = 0L)
    }
  }

  override fun highlightAnimated(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    durationMs: Double,
  ) {
    if (width <= 0.0 || height <= 0.0) return
    UiThreadUtil.runOnUiThread {
      val entering = lastWindowRect == null
      lastWindowRect = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
      spotlightView.setHighlight(
        xDp        = x.toFloat(),
        yDp        = y.toFloat(),
        widthDp    = width.toFloat(),
        heightDp   = height.toFloat(),
        animated   = true,
        durationMs = durationMs.toLong(),
      )
      emitTargetLayout()
      showHeaderDim()
      animateHeaderDimIn(entering, durationMs.toLong())
    }
  }

  override fun clear() {
    UiThreadUtil.runOnUiThread {
      lastWindowRect = null
      val exitMs = (exitDurationMsValue ?: DEFAULT_EXIT_DURATION_MS).toLong()
      // The overlay runs the configured exit animation; the header strip is
      // dropped when it finishes. A highlight arriving mid-exit cancels the
      // animation and calls showHeaderDim() itself, so a skipped onFinished
      // is safe.
      spotlightView.clear(durationMs = exitMs) { hideHeaderDim() }
      if (headerDimAdded && exitMs > 0L &&
        (exitAnimationValue ?: TRANSITION_ZOOM) == TRANSITION_FADE
      ) {
        // Fade the status-bar/header strip together with the overlay.
        headerDimView.animate().cancel()
        // Don't reset alpha here: the view is still attached until the posted
        // removal runs, and snapping back to 1 would flash the header dim.
        // removeHeaderDimNow() restores alpha after detaching it.
        headerDimView.animate().alpha(0f).setDuration(exitMs).withEndAction {
          if (lastWindowRect == null) hideHeaderDim()
        }.start()
      }
    }
  }

  /**
   * Header strip counterpart of the overlay's entering animation: fade it in
   * together with the overlay when the spotlight first appears.
   */
  private fun animateHeaderDimIn(entering: Boolean, durationMs: Long) {
    headerDimView.animate().cancel()
    val fade = entering && durationMs > 0L &&
      (enteringAnimationValue ?: TRANSITION_ZOOM) == TRANSITION_FADE
    if (fade) {
      headerDimView.alpha = 0f
      headerDimView.animate().alpha(1f).setDuration(durationMs).start()
    } else {
      headerDimView.alpha = 1f
    }
  }

  // -------------------------------------------------------------------------
  // RecyclableView
  // -------------------------------------------------------------------------

  override fun prepareForRecycle() {
    onTargetLayout = null
    onBackdropPress = null
    dimOpacityValue = null
    shapeValue = null
    borderRadiusValue = null
    paddingValue = null
    borderWidthValue = null
    borderColorValue = null
    allowOverlayClickValue = null
    enteringAnimationValue = null
    exitAnimationValue = null
    exitDurationMsValue = null
    lastWindowRect = null
    UiThreadUtil.runOnUiThread {
      spotlightView.dimOpacity = DEFAULT_DIM_OPACITY.toFloat()
      spotlightView.shape = DEFAULT_SHAPE
      spotlightView.borderRadius = DEFAULT_BORDER_RADIUS.toFloat()
      spotlightView.padding = DEFAULT_PADDING.toFloat()
      spotlightView.borderWidth = DEFAULT_BORDER_WIDTH.toFloat()
      spotlightView.borderColor = DEFAULT_BORDER_COLOR
      spotlightView.allowOverlayClick = DEFAULT_ALLOW_OVERLAY_CLICK
      spotlightView.enteringAnimation = TRANSITION_ZOOM
      spotlightView.exitAnimation = TRANSITION_ZOOM
      spotlightView.onBackdropPress = null
      lastWindowRect = null
      spotlightView.clear(durationMs = 0L)
      hideHeaderDim()
      decorView = null
    }
  }

  // -------------------------------------------------------------------------
  // Coordinate helpers
  // -------------------------------------------------------------------------

  /**
   * Convert a measureInWindow rect (DIP relative to visibleWindowFrame) into
   * a Rect expressed in the SpotlightOverlayView's local DIP coordinates.
   *
   * On non-edge-to-edge devices the values are identical. On edge-to-edge
   * (mandatory on Android 15+) the overlay sits at physical y=0 while
   * measureInWindow is relative to visibleWindowFrame.top, so y is offset by
   * the status-bar height. Using local DIP ensures SpotlightTooltip positions
   * correctly regardless of windowing mode.
   */
  private fun emitTargetLayout() {
    val rect = lastWindowRect ?: return
    onTargetLayout?.invoke(localDipRect(rect))
  }

  private fun localDipRect(windowDp: RectF): com.margelo.nitro.spotlight.Rect {
    val local = spotlightView.windowDpToLocalDip(windowDp)
    return if (local.isEmpty) {
      com.margelo.nitro.spotlight.Rect(
        x = windowDp.left.toDouble(),
        y = windowDp.top.toDouble(),
        width = windowDp.width().toDouble(),
        height = windowDp.height().toDouble(),
      )
    } else {
      com.margelo.nitro.spotlight.Rect(
        x      = local.left.toDouble(),
        y      = local.top.toDouble(),
        width  = local.width().toDouble(),
        height = local.height().toDouble(),
      )
    }
  }

  // -------------------------------------------------------------------------
  // Header dim: covers status bar + native nav header in decor-view
  // -------------------------------------------------------------------------

  private fun showHeaderDim() {
    // Only while a highlight is active — a late layout callback after clear()
    // must not resurrect the strip.
    if (lastWindowRect == null) return
    val dv = context.currentActivity?.window?.decorView as? ViewGroup ?: return

    // Skip if the spotlight overlay lives in a different window than the
    // activity (e.g. a BottomSheetDialogFragment or any modal dialog).
    // The host dialog provides its own scrim; adding a dim strip to the
    // activity's decor view would place it behind the dialog — invisible
    // and incorrect. The dim + cutout inside the dialog window is enough.
    if (spotlightView.windowToken != dv.windowToken) return

    decorView = dv

    // Not laid out yet: getLocationOnScreen would report [0,0]. The overlay
    // calls onGeometryChanged after its first layout, which re-enters here, so
    // no polling/retry is needed.
    if (!spotlightView.isLaidOut) return

    // Overlay sized from the window (zero-size parent, e.g. FullWindowOverlay
    // on Android): its screen position says nothing about how much sits above
    // the React tree, so don't guess a header strip.
    if (!spotlightView.isFittedToParent) {
      hideHeaderDim()
      return
    }

    // Measure how many pixels sit above the React-managed spotlightView
    // (status bar height + native navigation header height).
    val origin = IntArray(2)
    spotlightView.getLocationOnScreen(origin)

    // origin[1] is the screen-y of spotlightView's top edge.
    // Everything from y=0 to y=origin[1] is above the React tree.
    val coveredHeight = origin[1]
    if (coveredHeight <= 0) {
      // Overlay starts at the top of the window (root-level portal host, no
      // native header, immersive mode): nothing sits above it to cover.
      hideHeaderDim()
      return
    }

    // If the padded hole extends above spotlightView's top (target near the top
    // of the overlay), shrink headerDimView so its bottom doesn't overlap the
    // hole — avoiding the z-order issue where headerDimView (a decorView child
    // drawn above the React tree) dims part of the cutout.
    val effectiveCoveredHeight = (coveredHeight - spotlightView.holeOverreach().toInt()).coerceAtLeast(0)

    // Update color in case dimOpacity changed since last time.
    headerDimView.setBackgroundColor(dimArgb(dimOpacityValue ?: DEFAULT_DIM_OPACITY))
    syncHeaderDimTouch()

    // hideHeaderDim() sets headerDimAdded = false then asynchronously posts
    // removeView. If showHeaderDim fires again before that post runs, the view is
    // still attached — addView would throw "already has a parent". Re-adopt it
    // (the posted removal re-checks headerDimAdded and backs off).
    if (headerDimView.parent === dv) {
      headerDimAdded = true
      val params = headerDimView.layoutParams as? FrameLayout.LayoutParams
      if (params != null && params.height != effectiveCoveredHeight) {
        params.height = effectiveCoveredHeight
        headerDimView.layoutParams = params
      }
      return
    }

    dv.addView(
      headerDimView,
      FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, effectiveCoveredHeight),
    )
    headerDimAdded = true
  }

  /**
   * Touch contract for the header strip, mirroring the overlay:
   *  - default: blocks touches (clickable) and fires onBackdropPress on click;
   *  - allowOverlayClick: lets touches pass through to the native header but
   *    still fires onBackdropPress (once, on DOWN).
   */
  private fun syncHeaderDimTouch() {
    val passThrough = allowOverlayClickValue ?: DEFAULT_ALLOW_OVERLAY_CLICK
    if (passThrough) {
      headerDimView.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onBackdropPress?.invoke()
        false
      }
      headerDimView.setOnClickListener(null)
      // Must come after setOnClickListener(): it forces isClickable = true.
      headerDimView.isClickable = false
    } else {
      headerDimView.setOnTouchListener(null)
      headerDimView.isClickable = true
      headerDimView.setOnClickListener { onBackdropPress?.invoke() }
    }
  }

  private fun hideHeaderDim() {
    if (!headerDimAdded) return
    headerDimAdded = false
    removeHeaderDimNow()
  }

  private fun removeHeaderDimNow() {
    val dv = decorView ?: return
    if (headerDimView.parent !== dv) return
    // Post so we never remove a child during the decor-view's own layout
    // traversal. Re-check headerDimAdded: a highlight may have re-adopted the
    // view between this call and the post running (clear() then highlight()).
    dv.post {
      if (!headerDimAdded && headerDimView.parent === dv) {
        dv.removeView(headerDimView)
        // Safe to restore now that it's detached (a fade-out leaves it at 0).
        headerDimView.animate().cancel()
        headerDimView.alpha = 1f
      }
    }
  }

  private fun dimArgb(opacity: Double): Int =
    Color.argb((opacity.coerceIn(0.0, 1.0) * 255).toInt(), 0, 0, 0)

  companion object {
    private const val DEFAULT_DIM_OPACITY = 0.55
    private const val DEFAULT_SHAPE = "rect"
    private const val DEFAULT_BORDER_RADIUS = 12.0
    private const val DEFAULT_PADDING = 6.0
    private const val DEFAULT_BORDER_WIDTH = 1.5
    private const val DEFAULT_BORDER_COLOR = "#FFFFFF"
    private const val DEFAULT_ALLOW_OVERLAY_CLICK = false
    private const val DEFAULT_EXIT_DURATION_MS = 200.0
  }
}
