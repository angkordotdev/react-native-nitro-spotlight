package com.margelo.nitro.spotlight

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.os.Trace
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.ReactPointerEventsView
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.NativeGestureUtil
import kotlin.math.abs

internal const val TRANSITION_ZOOM = "zoom"
internal const val TRANSITION_FADE = "fade"
internal const val TRANSITION_NONE = "none"

internal class SpotlightOverlayView(
  context: Context,
) : FrameLayout(context), ReactPointerEventsView {

  // -------------------------------------------------------------------------
  // Public properties
  // -------------------------------------------------------------------------

  var dimOpacity: Float = 0.55f
    set(value) {
      if (field == value) return
      field = value
      updateDimPaintColor()
      invalidate()
    }

  var shape: String = "rect"
    set(value) {
      if (field == value) return
      field = value
      rebuildHolePath()
      invalidate()
    }

  var borderRadius: Float = 12f
    set(value) {
      if (field == value) return
      field = value
      rebuildHolePath()
      invalidate()
    }

  var padding: Float = 6f
    set(value) {
      if (field == value) return
      field = value
      rebuildHolePath()
      invalidate()
    }

  var borderWidth: Float = 1.5f
    set(value) {
      if (field == value) return
      field = value
      updateRingStrokeWidth()
      invalidate()
    }

  var borderColor: String = "#FFFFFF"
    set(value) {
      if (field == value) return
      field = value
      ringPaint.color = parseBorderColor(value)
      invalidate()
    }

  var allowOverlayClick: Boolean = false
    set(value) {
      field = value
    }

  /** How the spotlight appears from idle: "zoom" (default), "fade" or "none". */
  var enteringAnimation: String = TRANSITION_ZOOM

  /** How the spotlight disappears on clear(): "zoom" (default), "fade" or "none". */
  var exitAnimation: String = TRANSITION_ZOOM

  var onBackdropPress: (() -> Unit)? = null

  /**
   * Invoked after a layout pass moved/resized this view while a highlight is
   * active (first layout, rotation, reparent) so the owner can re-emit a
   * corrected target rect to JS and refresh anything anchored to our origin.
   */
  var onGeometryChanged: (() -> Unit)? = null

  /**
   * JS must never pick this view as the touch target: the RN touch dispatcher
   * resolves its target geometrically (before native dispatch), so with AUTO
   * the full-size overlay would swallow every JS touch, including ones inside
   * the cutout. Blocking is done natively in dispatchTouchEvent instead.
   */
  override val pointerEvents: PointerEvents = PointerEvents.BOX_NONE

  // -------------------------------------------------------------------------
  // Drawing
  // -------------------------------------------------------------------------

  private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = dimColor()
  }

  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = parseBorderColor(borderColor)
    style = Paint.Style.STROKE
    strokeWidth = borderWidth.coerceAtLeast(0f) * resources.displayMetrics.density
  }

  // -------------------------------------------------------------------------
  // Geometry
  // -------------------------------------------------------------------------

  /**
   * The requested highlight rect expressed in React Native window coordinates
   * (DIP units, as returned by measureInWindow on Android).
   */
  private val windowRectDp = RectF()

  // Cached once per highlight/clear call — refreshed by refreshGeometryCache().
  // Calling getLocationOnScreen / getWindowVisibleDisplayFrame on every
  // ValueAnimator frame (60 fps) is expensive; these values don't change
  // during a 300 ms animation, so we snapshot them once up front.
  private val cachedOverlayOrigin = IntArray(2)
  private val cachedVisibleFrame = Rect()
  private val outerRectPath = Path()

  private val currentLocalPx = RectF()
  private val targetLocalPx = RectF()
  private val cutRect = RectF()
  private val overlayPath = Path().apply {
    fillType = Path.FillType.EVEN_ODD
  }
  private val holePath = Path()
  // Hit-test region — built lazily on ACTION_DOWN from the final hole path
  // instead of on every animation frame.
  private val holeRegion = Region()
  private val holeClipRegion = Region()
  private val holeRegionBounds = Rect()
  private val holePathBounds = RectF()

  // -------------------------------------------------------------------------
  // Animation
  // -------------------------------------------------------------------------

  private var activeAnimator: ValueAnimator? = null

  // Fade runs on the view's own alpha (the overlay has no drawn children — the
  // tooltip is a JS sibling), independent of the path animator so a fade-in can
  // overlap a cutout morph.
  private var fadeAnimator: ValueAnimator? = null

  // -------------------------------------------------------------------------
  // Touch state
  // -------------------------------------------------------------------------

  private var blockingTouch = false
  private var downX = 0f
  private var downY = 0f
  private var touchMoved = false
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

  // -------------------------------------------------------------------------
  // Display-metric cache (refreshed in onLayout)
  // -------------------------------------------------------------------------

  private var cachedDensity: Float = resources.displayMetrics.density

  // True when this overlay lives in a dialog/sheet window (different window
  // token from the host activity). Refreshed once per highlight in
  // refreshGeometryCache(). Used to select the correct coordinate origin in
  // windowDpToLocalPx(): dialog React roots are at cachedOverlayOrigin[1],
  // not at cachedVisibleFrame.top.
  private var cachedIsDialogWindow = false

  // The Y reference used to convert measureInWindow DIP coordinates back to
  // screen pixels. Equals the Y offset that RN subtracted from the target
  // view's screen position when computing measureInWindow. Cached once per
  // highlight in refreshGeometryCache() — see windowDpToLocalPx().
  private var cachedRefY: Float = 0f

  // -------------------------------------------------------------------------
  // Init
  // -------------------------------------------------------------------------

  /**
   * Fabric can size the Nitro host view from its own props (padding/border)
   * rather than the JS `style`, leaving a thin strip. The overlay must always
   * cover its parent (the <Spotlight> wrapper) for the dim and for touch
   * blocking to work, so mirror the parent's size whenever it changes.
   */
  private val parentLayoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
    fitToParent()
  }

  /**
   * True when the overlay was sized from a real (non-zero) parent. False when
   * the parent is zero-sized — e.g. react-native-screens' FullWindowOverlay,
   * which on Android is a plain style-less <View> — and we fell back to the
   * window size. Anything that assumes the overlay sits at the top of the
   * React content (the header dim strip) must not trust our position then.
   */
  var isFittedToParent = false
    private set

  private fun fitToParent() {
    val p = parent as? View ?: return
    val w: Int
    val h: Int
    if (p.width > 0 && p.height > 0) {
      w = p.width
      h = p.height
      isFittedToParent = true
    } else {
      // Zero-sized parent: it still doesn't clip (overflow: visible), so size
      // ourselves to the window and the dim/cutout draws correctly. Touches
      // can't be blocked outside a zero-size parent's bounds, though.
      val root = rootView
      w = root.width
      h = root.height
      isFittedToParent = false
      if (w <= 0 || h <= 0) return
    }
    if (left != 0 || top != 0 || width != w || height != h) {
      layout(0, 0, w, h)
    }
  }

  init {
    // FrameLayout/ViewGroup defaults to WILL_NOT_DRAW when it has no
    // background. We draw the dim overlay in onDraw(), so opt in explicitly.
    setWillNotDraw(false)

    isClickable = false
    isFocusable = false
    isFocusableInTouchMode = false
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

    // Fabric lays this view out itself (View.layout is final), so correct its
    // bounds after the fact whenever they differ from the parent's.
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitToParent() }
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  fun setHighlight(
    xDp: Float,
    yDp: Float,
    widthDp: Float,
    heightDp: Float,
    animated: Boolean,
    durationMs: Long = 250L,
  ) {
    if (widthDp <= 0f || heightDp <= 0f) {
      clear(durationMs = 0L)
      return
    }

    val nextWindowRectDp = RectF(xDp, yDp, xDp + widthDp, yDp + heightDp)

    if (animated && isAnimating() && windowRectDp.approximatelyEquals(nextWindowRectDp)) {
      return
    }

    // React Native measureInWindow returns DIP coordinates relative to the
    // visible app window. Store the original JS values; convert only when we
    // know this overlay's current screen position.
    windowRectDp.set(nextWindowRectDp)

    // Snapshot geometry even if this view isn't laid out yet — getLocationOnScreen
    // / getWindowVisibleDisplayFrame reflect this view's screen position, not its
    // size, so windowDpToLocalDip() (read immediately by callers via onTargetLayout)
    // gets accurate values instead of zero-initialized defaults.
    refreshGeometryCache()

    // Guard: if not laid out yet, onLayout will apply windowRectDp once it is.
    if (width == 0 || height == 0) return

    targetLocalPx.set(windowDpToLocalPx(windowRectDp))

    val entering = currentLocalPx.isEmpty
    // Resume point if this interrupts a fade-out (alpha < 1).
    val resumeAlpha = alpha

    if (!animated || durationMs <= 0L || (entering && enteringAnimation == TRANSITION_NONE)) {
      cancelAnimation()
      currentLocalPx.set(targetLocalPx)
      rebuildHolePath()
      invalidate()
      return
    }

    if (entering && enteringAnimation == TRANSITION_FADE) {
      cancelAnimation()
      currentLocalPx.set(targetLocalPx)
      rebuildHolePath()
      alpha = 0f
      invalidate()
      fadeTo(1f, durationMs)
      return
    }

    animateTo(targetLocalPx, durationMs)
    if (resumeAlpha < 1f || fadeAnimator?.isRunning == true) {
      // A new highlight interrupted a fade-out: bring the dim back while the
      // cutout morphs to the new target.
      alpha = resumeAlpha
      fadeTo(1f, durationMs)
    }
  }

  fun clear(durationMs: Long = 200L, onFinished: (() -> Unit)? = null) {
    if (windowRectDp.isEmpty && isAnimating()) {
      return
    }

    windowRectDp.setEmpty()

    if (durationMs <= 0L || currentLocalPx.isEmpty || exitAnimation == TRANSITION_NONE) {
      resetState()
      invalidate()
      onFinished?.invoke()
      return
    }

    // Snapshot geometry once before the exit animation starts.
    refreshGeometryCache()

    if (exitAnimation == TRANSITION_FADE) {
      // Keep the hole where it is and fade the whole overlay out.
      cancelPathAnimation()
      fadeTo(0f, durationMs) {
        resetState()
        invalidate()
        onFinished?.invoke()
      }
      return
    }

    fadeAnimator?.cancel()
    fadeAnimator = null
    alpha = 1f
    val centerX = currentLocalPx.centerX()
    val centerY = currentLocalPx.centerY()
    animateTo(RectF(centerX, centerY, centerX, centerY), durationMs, onFinished)
  }

  private fun resetState() {
    cancelAnimation()
    currentLocalPx.setEmpty()
    targetLocalPx.setEmpty()
    overlayPath.reset()
    holePath.reset()
  }

  private fun isAnimating(): Boolean =
    activeAnimator?.isRunning == true || fadeAnimator?.isRunning == true

  // -------------------------------------------------------------------------
  // Layout
  // -------------------------------------------------------------------------

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    // Refresh the cheap density cache whenever the view is re-laid-out
    // (rotation, window resize, density change). The geometry cache
    // (overlay origin, visible frame) is refreshed per highlight.
    cachedDensity = resources.displayMetrics.density

    if (changed && !windowRectDp.isEmpty) {
      refreshGeometryCache()
      targetLocalPx.set(windowDpToLocalPx(windowRectDp))
      cancelAnimation()
      currentLocalPx.set(targetLocalPx)
      rebuildHolePath()
      invalidate()
      onGeometryChanged?.invoke()
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    (parent as? View)?.addOnLayoutChangeListener(parentLayoutListener)
    // Neither listener fires for layout that already happened before attach.
    fitToParent()
    post { fitToParent() }
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  override fun onDetachedFromWindow() {
    (parent as? View)?.removeOnLayoutChangeListener(parentLayoutListener)
    cancelAnimation()
    super.onDetachedFromWindow()
  }

  // -------------------------------------------------------------------------
  // Drawing
  // -------------------------------------------------------------------------

  override fun onDraw(canvas: Canvas) = traceSection(TRACE_ON_DRAW) {
    super.onDraw(canvas)

    if (!hasActiveSpotlight()) return@traceSection

    canvas.drawPath(overlayPath, dimPaint)
    if (borderWidth > 0f) {
      canvas.drawPath(holePath, ringPaint)
    }
  }

  // -------------------------------------------------------------------------
  // Touch handling
  //
  // Strategy: never intercept. On DOWN, decide once whether the touch should
  // pass through to RN underneath or be blocked by the overlay. Carry that
  // decision through the rest of the gesture.
  //
  // When there is no active spotlight, dispatchTouchEvent returns false
  // immediately so Android continues hit-testing the next view in the
  // hierarchy — touches fall through to React Native content underneath.
  // -------------------------------------------------------------------------

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean = false

  override fun dispatchTouchEvent(event: MotionEvent): Boolean = traceSection(TRACE_TOUCH) {
    // No spotlight active — let every touch fall through to RN underneath.
    if (!hasActiveSpotlight()) return@traceSection false

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val isBackdropTouch = !isTouchInsideHole(event.x.toInt(), event.y.toInt())

        if (allowOverlayClick) {
          // Pass-through: the gesture belongs to whatever is underneath, but
          // onBackdropPress still fires (once, on DOWN) for backdrop touches.
          if (isBackdropTouch) onBackdropPress?.invoke()
          blockingTouch = false
        } else {
          blockingTouch = isBackdropTouch
          if (blockingTouch) {
            downX = event.x
            downY = event.y
            touchMoved = false
            // JS already started a touch on the view underneath (the RN touch
            // dispatcher runs before native dispatch). Cancel it so blocked
            // backdrop taps don't also press JS views below the dim.
            NativeGestureUtil.notifyNativeGestureStarted(this, event)
          }
        }
        // Returning false for cutout touches (and in pass-through mode) lets
        // Android continue hit-testing to the views underneath.
        blockingTouch
      }
      MotionEvent.ACTION_MOVE -> {
        if (blockingTouch && !touchMoved &&
          (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)
        ) {
          touchMoved = true
        }
        blockingTouch
      }
      MotionEvent.ACTION_UP -> {
        val wasBlocking = blockingTouch
        blockingTouch = false
        if (wasBlocking && !touchMoved && !isTouchInsideHole(event.x.toInt(), event.y.toInt())) {
          onBackdropPress?.invoke()
        }
        wasBlocking
      }
      MotionEvent.ACTION_CANCEL -> {
        val wasBlocking = blockingTouch
        blockingTouch = false
        wasBlocking
      }
      else -> blockingTouch
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean = false

  // -------------------------------------------------------------------------
  // Geometry helpers
  // -------------------------------------------------------------------------

  /**
   * Hit-test against the current hole path regardless of allowOverlayClick —
   * onBackdropPress must only fire for real backdrop touches in both modes.
   */
  private fun isTouchInsideHole(touchX: Int, touchY: Int): Boolean {
    if (holePath.isEmpty) return false
    holePath.computeBounds(holePathBounds, true)
    holePathBounds.roundOut(holeRegionBounds)
    holeClipRegion.set(holeRegionBounds)
    holeRegion.setPath(holePath, holeClipRegion)
    return holeRegion.contains(touchX, touchY)
  }

  /**
   * Pixels by which the padded hole extends above this view's top boundary.
   * Used by HybridSpotlightView to shrink headerDimView so it never overlaps
   * the hole when the target sits near the top of the overlay.
   */
  fun holeOverreach(): Float {
    if (targetLocalPx.isEmpty) return 0f
    return maxOf(0f, padding * cachedDensity - targetLocalPx.top)
  }

  /**
   * Convert a React Native measureInWindow rect into this overlay's local
   * DIP coordinates. Used by HybridSpotlightView to report onTargetLayout in
   * the same coordinate space that SpotlightTooltip uses for positioning.
   *
   * On non-edge-to-edge devices the result equals the input (overlay top ==
   * visibleWindowFrame.top). On edge-to-edge devices (mandatory on Android 15+)
   * the overlay sits at physical y=0 while measureInWindow is relative to
   * visibleWindowFrame.top, so this adds the status-bar height to x/y, aligning
   * the rect with the overlay's local origin.
   */
  fun windowDpToLocalDip(windowDp: RectF): RectF {
    val localPx = windowDpToLocalPx(windowDp)
    if (localPx.isEmpty) return RectF()
    return RectF(
      localPx.left / cachedDensity,
      localPx.top / cachedDensity,
      localPx.right / cachedDensity,
      localPx.bottom / cachedDensity,
    )
  }

  /**
   * Convert a React Native measureInWindow rect into this overlay's local
   * pixel coordinates using the cached geometry snapshot.
   *
   * Android RN returns measureInWindow in DIPs and relative to the visible
   * window frame, while Android Views draw/hit-test in physical pixels and
   * getLocationOnScreen() is screen-space. Reconstruct screen pixels by
   * multiplying by density and adding the visible window frame offset, then
   * subtract this overlay's screen origin.
   *
   * Must call refreshGeometryCache() before using this during an animation.
   */
  private fun windowDpToLocalPx(windowDp: RectF): RectF = traceSection(TRACE_WINDOW_TO_LOCAL) {
    if (windowDp.isEmpty) return@traceSection RectF()

    val screenLeft = windowDp.left * cachedDensity + cachedVisibleFrame.left
    val screenTop = windowDp.top * cachedDensity + cachedRefY
    val screenRight = windowDp.right * cachedDensity + cachedVisibleFrame.left
    val screenBottom = windowDp.bottom * cachedDensity + cachedRefY

    RectF(
      screenLeft - cachedOverlayOrigin[0],
      screenTop - cachedOverlayOrigin[1],
      screenRight - cachedOverlayOrigin[0],
      screenBottom - cachedOverlayOrigin[1],
    )
  }

  /**
   * Snapshot values that require system calls — called once per highlight/clear,
   * not per animation frame. Eliminates ~2 system calls × 18 frames @ 60 fps
   * during a typical 300 ms transition.
   */
  private fun refreshGeometryCache() {
    getLocationOnScreen(cachedOverlayOrigin)
    getWindowVisibleDisplayFrame(cachedVisibleFrame)

    // Detect dialog windows (BottomSheet, formSheet) by comparing our window
    // token to the host activity's decor window token. In a dialog the React
    // root sits at cachedOverlayOrigin[1] on screen; in the main activity it
    // sits at cachedVisibleFrame.top (status-bar height). The two cases need
    // different reference Y values in windowDpToLocalPx().
    cachedIsDialogWindow = run {
      val actToken = (context as? ThemedReactContext)
        ?.currentActivity?.window?.decorView?.windowToken
        ?: return@run false
      val myToken = windowToken ?: return@run false
      myToken != actToken
    }

    // Compute the Y reference for windowDpToLocalPx():
    //   • Main activity window: React root is at visibleFrame.top.
    //   • Bottom-sheet / dialog with the overlay INSIDE the dialog:
    //       overlay origin > 0 (the dialog sits partway down the screen) and
    //       measureInWindow uses the dialog's own React root, which sits at
    //       cachedOverlayOrigin[1]. Use that as refY.
    //   • FullWindowOverlay (react-native-screens) and similar full-screen
    //       foreign windows: the overlay is at physical y=0 (cachedOverlayOrigin[1]
    //       == 0) but the targets are still measured by the MAIN window's React
    //       root. Using origin[1] == 0 as refY misses the status-bar offset.
    //       Read the main activity's visible frame top instead.
    cachedRefY = when {
      !cachedIsDialogWindow -> cachedVisibleFrame.top.toFloat()
      cachedOverlayOrigin[1] > 0 -> cachedOverlayOrigin[1].toFloat()
      else -> {
        val frame = Rect()
        (context as? ThemedReactContext)?.currentActivity?.window?.decorView
          ?.getWindowVisibleDisplayFrame(frame)
        frame.top.toFloat()
      }
    }

    // The EVEN_ODD outer rect only needs to contain the hole and the whole
    // visible area. A very large rect is independent of window metrics, so it
    // stays correct in split-screen / freeform windows where displayMetrics
    // (window size) and getLocationOnScreen (display coordinates) disagree.
    // Built once here; the canvas clip bounds what is actually rasterized.
    if (outerRectPath.isEmpty) {
      outerRectPath.addRect(-OUTER_EXTENT, -OUTER_EXTENT, OUTER_EXTENT, OUTER_EXTENT, Path.Direction.CW)
    }
  }

  private fun rebuildHolePath() = traceSection(TRACE_REBUILD_HOLE) {
    overlayPath.reset()
    overlayPath.fillType = Path.FillType.EVEN_ODD
    holePath.reset()

    if (currentLocalPx.isEmpty || width == 0 || height == 0) return@traceSection

    val pad = padding * cachedDensity
    val radius = (borderRadius + padding).coerceAtLeast(0f) * cachedDensity

    cutRect.set(
      currentLocalPx.left - pad,
      currentLocalPx.top - pad,
      currentLocalPx.right + pad,
      currentLocalPx.bottom + pad,
    )

    // Use addRoundRect for both shapes so the path structure stays identical
    // (same element count/types) — allowing smooth ValueAnimator morphing between
    // rect and circle within the same tour. addOval produces a different internal
    // path structure and would cause an instant snap instead of a morph.
    val holeRadius = if (shape == "circle") {
      minOf(cutRect.width(), cutRect.height()) / 2f
    } else {
      radius
    }
    holePath.addRoundRect(cutRect, holeRadius, holeRadius, Path.Direction.CW)

    // Use the pre-built outer rect (physical screen bounds in local coords).
    // See refreshGeometryCache() for why we use screen bounds instead of
    // view bounds or visibleWindowFrame.
    overlayPath.addPath(outerRectPath)
    overlayPath.addPath(holePath)
  }

  private fun hasActiveSpotlight(): Boolean =
    !currentLocalPx.isEmpty && !overlayPath.isEmpty

  // -------------------------------------------------------------------------
  // Animation
  // -------------------------------------------------------------------------

  private fun animateTo(target: RectF, durationMs: Long, onFinished: (() -> Unit)? = null) {
    cancelPathAnimation()

    if (durationMs <= 0L) {
      currentLocalPx.set(target)
      rebuildHolePath()
      invalidate()
      return
    }

    val from = if (currentLocalPx.isEmpty && !target.isEmpty) {
      RectF(target.centerX(), target.centerY(), target.centerX(), target.centerY())
    } else {
      RectF(currentLocalPx)
    }

    val to = RectF(target)

    activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = durationMs
      interpolator = DecelerateInterpolator()

      addUpdateListener { animation ->
        val p = animation.animatedValue as Float
        currentLocalPx.set(
          lerp(from.left,   to.left,   p),
          lerp(from.top,    to.top,    p),
          lerp(from.right,  to.right,  p),
          lerp(from.bottom, to.bottom, p),
        )
        rebuildHolePath()
        invalidate()
      }

      addListener(object : Animator.AnimatorListener {
        // Animator.cancel() calls onAnimationCancel() and then onAnimationEnd().
        // A cancelled animation was superseded — its owner already set up the
        // next state, so the end logic must not run (it would reset the
        // geometry the replacement animation starts from).
        private var cancelled = false

        override fun onAnimationStart(animation: Animator) = Unit

        override fun onAnimationEnd(animation: Animator) {
          if (cancelled) return
          if (activeAnimator === animation) activeAnimator = null
          if (windowRectDp.isEmpty) {
            // Clear animation finished — reset everything.
            resetState()
            invalidate()
            onFinished?.invoke()
          }
        }

        override fun onAnimationCancel(animation: Animator) {
          cancelled = true
          if (activeAnimator === animation) activeAnimator = null
        }

        override fun onAnimationRepeat(animation: Animator) = Unit
      })

      start()
    }
  }

  private fun cancelPathAnimation() {
    activeAnimator?.cancel()
    activeAnimator = null
  }

  /** Cancels any path/fade animation and restores full opacity. */
  private fun cancelAnimation() {
    cancelPathAnimation()
    fadeAnimator?.cancel()
    fadeAnimator = null
    alpha = 1f
  }

  private fun fadeTo(target: Float, durationMs: Long, onFinished: (() -> Unit)? = null) {
    fadeAnimator?.cancel()
    fadeAnimator = ValueAnimator.ofFloat(alpha, target).apply {
      duration = durationMs
      interpolator = DecelerateInterpolator()
      addUpdateListener { alpha = it.animatedValue as Float }
      addListener(object : Animator.AnimatorListener {
        // cancel() also calls onAnimationEnd(); a cancelled fade was superseded.
        private var cancelled = false

        override fun onAnimationStart(animation: Animator) = Unit

        override fun onAnimationEnd(animation: Animator) {
          if (cancelled) return
          if (fadeAnimator === animation) fadeAnimator = null
          alpha = if (target <= 0f && windowRectDp.isEmpty) 1f else target
          onFinished?.invoke()
        }

        override fun onAnimationCancel(animation: Animator) {
          cancelled = true
          if (fadeAnimator === animation) fadeAnimator = null
        }

        override fun onAnimationRepeat(animation: Animator) = Unit
      })
      start()
    }
  }

  private fun RectF.approximatelyEquals(other: RectF, tolerance: Float = 0.5f): Boolean =
    kotlin.math.abs(left - other.left) <= tolerance &&
      kotlin.math.abs(top - other.top) <= tolerance &&
      kotlin.math.abs(right - other.right) <= tolerance &&
      kotlin.math.abs(bottom - other.bottom) <= tolerance

  private fun lerp(from: Float, to: Float, progress: Float): Float =
    from + (to - from) * progress

  private inline fun <T> traceSection(name: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block()

    Trace.beginSection(name)
    return try {
      block()
    } finally {
      Trace.endSection()
    }
  }

  private fun updateDimPaintColor() {
    dimPaint.color = dimColor()
  }

  private fun updateRingStrokeWidth() {
    ringPaint.strokeWidth = borderWidth.coerceAtLeast(0f) * cachedDensity
  }

  private fun dimColor(): Int = Color.argb(
    (dimOpacity.coerceIn(0f, 1f) * 255).toInt(),
    0, 0, 0,
  )

  private fun parseBorderColor(value: String): Int =
    runCatching { Color.parseColor(value) }.getOrDefault(Color.WHITE)

  private companion object {
    private const val TRACE_ON_DRAW = "Spotlight.onDraw"
    private const val TRACE_TOUCH = "Spotlight.dispatchTouchEvent"
    private const val TRACE_WINDOW_TO_LOCAL = "Spotlight.windowDpToLocalPx"
    private const val TRACE_REBUILD_HOLE = "Spotlight.rebuildHolePath"
    private const val OUTER_EXTENT = 20000f
  }
}
