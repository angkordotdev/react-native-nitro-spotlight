import UIKit

enum SpotlightShape: String {
  case rect
  case circle
}

/// Transition used when the spotlight appears from idle / is cleared.
enum SpotlightTransition: String {
  case zoom
  case fade
  case none
}

public final class SpotlightView: UIView {

  // MARK: - Config

  // Style-only props (dim/ring) never touch the path layers, so they can't
  // cancel an in-flight highlight animation.
  var dimOpacity: CGFloat = 0.55 {
    didSet {
      guard oldValue != dimOpacity else { return }
      applyStyle()
    }
  }

  // Geometry props re-animate from the current (presentation) path while a
  // highlight is showing, so e.g. a per-step `shape` change morphs instead of
  // snapping on the previous target.
  var borderRadius: CGFloat = 12 {
    didSet {
      guard oldValue != borderRadius else { return }
      redrawForGeometryChange()
    }
  }

  var padding: CGFloat = 6 {
    didSet {
      guard oldValue != padding else { return }
      redrawForGeometryChange()
    }
  }

  var borderWidth: CGFloat = 0 {
    didSet {
      guard oldValue != borderWidth else { return }
      applyStyle()
    }
  }

  var borderColor: String = "#FFFFFF" {
    didSet {
      guard oldValue != borderColor else { return }
      resolvedBorderColor = UIColor.spotlightColor(from: borderColor)
      applyStyle()
    }
  }

  var shape: SpotlightShape = .rect {
    didSet {
      guard oldValue != shape else { return }
      redrawForGeometryChange()
    }
  }

  var allowOverlayClick = false

  var enteringAnimation: SpotlightTransition = .zoom
  var exitAnimation: SpotlightTransition = .zoom
  var exitDuration: TimeInterval = 0.2

  var onBackdropPress: (() -> Void)?


  // MARK: - Private

  private let spotlightMask = CAShapeLayer()
  private let ringLayer = CAShapeLayer()

  /// Rect of the cutout (highlighted target). Window-space coords from measureInWindow.
  private var sourceRect = CGRect.zero
  private var currentOverlayPath: UIBezierPath?
  private var currentHolePath: UIBezierPath?
  private var resolvedBorderColor = UIColor.white
  private var needsRedrawAfterAnimation = false

  // MARK: - Init

  override init(frame: CGRect) {
    super.init(frame: frame)

    spotlightMask.fillRule = .evenOdd
    spotlightMask.fillColor = UIColor.black.withAlphaComponent(dimOpacity).cgColor
    layer.addSublayer(spotlightMask)

    ringLayer.fillColor = UIColor.clear.cgColor
    ringLayer.strokeColor = resolvedBorderColor.cgColor
    ringLayer.lineWidth = borderWidth
    ringLayer.isHidden = borderWidth <= 0
    layer.addSublayer(ringLayer)
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  // MARK: - Layout

  public override func didMoveToWindow() {
    super.didMoveToWindow()
    guard window != nil else { return }
    syncFrameToWindow()
    updateLayerFrames()
    // Pre-warm layers to avoid first render stutter.
    spotlightMask.path = UIBezierPath(rect: .zero).cgPath
    ringLayer.path = UIBezierPath(rect: .zero).cgPath
    redraw(animated: false)
  }

  public override func didMoveToSuperview() {
    super.didMoveToSuperview()
    setNeedsLayout()
  }

  public override func layoutSubviews() {
    super.layoutSubviews()
    syncFrameToWindow()
    updateLayerFrames()
    // Skip while a highlight transition is animating — any layout pass
    // (keyboard, safe-area change, parent resize) would otherwise cancel
    // the in-flight CABasicAnimation and snap the mask to its final path.
    // Remember it though: the paths are stale until the animation finishes.
    guard !hasRunningPathAnimation else {
      needsRedrawAfterAnimation = true
      return
    }
    redraw(animated: false)
  }

  // MARK: - Frame Sync

  /// Fabric can lay the Nitro host view out with a collapsed frame (the JS
  /// `style` does not reach the host's Yoga node), and UIKit never hit-tests
  /// a view whose ancestor frames don't contain the touch — so backdrop
  /// touches would never reach hitTest/point(inside:). Drawing already covers
  /// the window regardless of frame (see makeOverlayPath); mirror that for
  /// touch by keeping self and the host superview sized to the window.
  private func syncFrameToWindow() {
    guard let window, let host = superview, let hostParent = host.superview else { return }
    let targetHostFrame = hostParent.convert(window.bounds, from: window)
    if !host.frame.isApproximatelyEqual(to: targetHostFrame) {
      host.frame = targetHostFrame
    }
    if !frame.isApproximatelyEqual(to: host.bounds) {
      frame = host.bounds
    }
  }

  // MARK: - Layer Frames

  private func updateLayerFrames() {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    // Keep layer coordinates same as this UIView — never use UIScreen.main.bounds.
    spotlightMask.frame = bounds
    ringLayer.frame = bounds
    CATransaction.commit()
  }

  // MARK: - Public API

  func setHighlight(
    _ rect: CGRect,
    animated: Bool,
    duration: TimeInterval = 0.25
  ) {
    if animated,
       rect.isApproximatelyEqual(to: sourceRect),
       hasRunningPathAnimation {
      return
    }
    sourceRect = rect
    syncFrameToWindow()
    redraw(animated: animated, duration: duration)
  }

  func clear(
    animated: Bool = true,
    duration: TimeInterval = 0.2
  ) {
    if sourceRect.isEmpty, hasRunningPathAnimation { return }
    sourceRect = .zero
    redraw(animated: animated, duration: duration)
  }

  // MARK: - Touch Handling

  /// UIKit calls hitTest more than once per touch (and for non-touch queries),
  /// so pass-through mode must dedupe by event timestamp to fire exactly once.
  private var lastBackdropEventTimestamp: TimeInterval = -1

  public override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    guard !sourceRect.isEmpty else { return nil }
    guard isBackdropPoint(point) else { return nil }
    if allowOverlayClick {
      if let event, event.type == .touches, event.timestamp != lastBackdropEventTimestamp {
        lastBackdropEventTimestamp = event.timestamp
        onBackdropPress?()
      }
      return nil
    }
    return self
  }

  public override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    guard !sourceRect.isEmpty, !allowOverlayClick else { return false }
    return isBackdropPoint(point)
  }

  public override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesEnded(touches, with: event)
    guard let touchPoint = touches.first?.location(in: self),
          point(inside: touchPoint, with: event)
    else { return }
    onBackdropPress?()
  }

  private func isBackdropPoint(_ point: CGPoint) -> Bool {
    !(currentHolePath?.contains(point) ?? false)
  }

  // MARK: - Coordinate Conversion

  /// Converts a measureInWindow rect into the coordinate space of the
  /// `<Spotlight>` wrapper (the host's superview), which is where JS renders
  /// the tooltip. Matches Android's overlay-local `onTargetLayout` rect.
  func wrapperRect(fromWindowRect rect: CGRect) -> Rect {
    var converted = rect
    if let window, let wrapper = superview?.superview {
      converted = wrapper.convert(rect, from: window)
    }
    return Rect(
      x: Double(converted.origin.x),
      y: Double(converted.origin.y),
      width: Double(converted.size.width),
      height: Double(converted.size.height)
    )
  }

  private func localRect(from rect: CGRect) -> CGRect {
    guard let window else { return rect }
    return window.convert(rect, to: self)
  }

  // MARK: - Drawing

  /// Updates fill/stroke style without touching the path layers or any
  /// in-flight path animation.
  private func applyStyle() {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    spotlightMask.fillColor = UIColor.black.withAlphaComponent(dimOpacity).cgColor
    ringLayer.isHidden = borderWidth <= 0
    ringLayer.strokeColor = resolvedBorderColor.cgColor
    ringLayer.lineWidth = borderWidth
    CATransaction.commit()
  }

  private func redrawForGeometryChange() {
    redraw(animated: !sourceRect.isEmpty)
  }

  private func redraw(
    animated: Bool,
    duration: TimeInterval = 0.25
  ) {
    applyStyle()

    let nextHolePath = makeHolePath()
    let nextOverlayPath = makeOverlayPath(holePath: nextHolePath)
    let nextRingPath = makeRingPath(holePath: nextHolePath)

    let oldOverlayPath = currentOverlayPath
    let oldHolePath = currentHolePath
    currentOverlayPath = nextOverlayPath
    currentHolePath = nextHolePath
    needsRedrawAfterAnimation = false

    // Opacity to resume a fade from if one is interrupted mid-flight.
    let runningFade = spotlightMask.animation(forKey: Self.fadeKey) != nil
    let fadeStartOpacity: Float = runningFade ? (spotlightMask.presentation()?.opacity ?? 1) : 1
    spotlightMask.removeAnimation(forKey: Self.fadeKey)
    ringLayer.removeAnimation(forKey: Self.fadeKey)
    setLayerOpacity(1)

    let isEntering = nextHolePath != nil && oldHolePath == nil
    let isExiting = nextHolePath == nil && oldHolePath != nil
    let transition: SpotlightTransition =
      isEntering ? enteringAnimation : (isExiting ? exitAnimation : .zoom)

    guard animated, !((isEntering || isExiting) && transition == .none) else {
      CATransaction.begin()
      CATransaction.setDisableActions(true)
      spotlightMask.removeAnimation(forKey: "path")
      spotlightMask.path = nextOverlayPath?.cgPath

      ringLayer.removeAnimation(forKey: "path")
      ringLayer.path = nextRingPath?.cgPath
      CATransaction.commit()
      return
    }

    // Fade: the cutout stays put and only the dim/ring opacity animates.
    if (isEntering || isExiting) && transition == .fade {
      CATransaction.begin()
      CATransaction.setDisableActions(true)
      spotlightMask.removeAnimation(forKey: "path")
      ringLayer.removeAnimation(forKey: "path")
      // Exiting keeps showing the old hole while it fades out.
      spotlightMask.path = (isExiting ? oldOverlayPath : nextOverlayPath)?.cgPath
      ringLayer.path = (isExiting ? oldHolePath : nextRingPath)?.cgPath
      CATransaction.commit()

      CATransaction.begin()
      CATransaction.setCompletionBlock { [weak self] in
        self?.pathAnimationDidFinish()
      }
      if isEntering {
        fadeLayers(from: runningFade ? fadeStartOpacity : 0, to: 1, duration: duration)
      } else {
        fadeLayers(from: fadeStartOpacity, to: 0, duration: duration)
      }
      CATransaction.commit()
      return
    }

    // Show from idle grows the hole out of the target centre; clear collapses
    // it back into the old centre. Both use a 1pt rounded rect so the path
    // structure matches the real hole and Core Animation can interpolate.
    var fromOverlay = oldOverlayPath?.cgPath
    var fromRing = oldHolePath?.cgPath
    var toOverlay = nextOverlayPath?.cgPath
    var toRing = nextRingPath?.cgPath

    if let nextHolePath, oldHolePath == nil {
      let collapsed = collapsedHolePath(around: nextHolePath.bounds)
      fromOverlay = makeOverlayPath(holePath: collapsed)?.cgPath
      fromRing = collapsed.cgPath
    } else if nextHolePath == nil, let oldHolePath {
      let collapsed = collapsedHolePath(around: oldHolePath.bounds)
      toOverlay = makeOverlayPath(holePath: collapsed)?.cgPath
      toRing = collapsed.cgPath
    }

    CATransaction.begin()
    CATransaction.setCompletionBlock { [weak self] in
      self?.pathAnimationDidFinish()
    }
    animate(layer: spotlightMask, from: fromOverlay, to: toOverlay, duration: duration)
    animate(layer: ringLayer, from: fromRing, to: toRing, duration: duration)
    CATransaction.commit()
  }

  private static let fadeKey = "fade"

  private func setLayerOpacity(_ value: Float) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    spotlightMask.opacity = value
    ringLayer.opacity = value
    CATransaction.commit()
  }

  /// Animates both layers' opacity. The model value is set to `to` up front so
  /// the layers don't snap back when the animation is removed.
  private func fadeLayers(from: Float, to: Float, duration: TimeInterval) {
    setLayerOpacity(to)
    for layer in [spotlightMask, ringLayer] {
      let anim = CABasicAnimation(keyPath: "opacity")
      anim.fromValue = from
      anim.toValue = to
      anim.duration = duration
      anim.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
      anim.isRemovedOnCompletion = true
      layer.add(anim, forKey: Self.fadeKey)
    }
  }

  private func pathAnimationDidFinish() {
    // A newer animation took over — it owns the cleanup.
    guard !hasRunningPathAnimation else { return }
    if sourceRect.isEmpty {
      // Collapse finished: drop the collapsed hole so nothing stays dimmed.
      CATransaction.begin()
      CATransaction.setDisableActions(true)
      spotlightMask.path = nil
      ringLayer.path = nil
      spotlightMask.opacity = 1
      ringLayer.opacity = 1
      CATransaction.commit()
    } else if needsRedrawAfterAnimation {
      redraw(animated: false)
    }
  }

  private func collapsedHolePath(around rect: CGRect) -> UIBezierPath {
    let center = CGPoint(x: rect.midX, y: rect.midY)
    return UIBezierPath(
      roundedRect: CGRect(x: center.x - 0.5, y: center.y - 0.5, width: 1, height: 1),
      cornerRadius: 0.5
    )
  }

  private func makeHolePath() -> UIBezierPath? {
    guard !sourceRect.isEmpty else { return nil }
    let local = localRect(from: sourceRect)
    let cutRect = local.insetBy(dx: -padding, dy: -padding)
    switch shape {
    case .circle:
      // Use roundedRect with max corner radius instead of ovalIn so iOS
      // CABasicAnimation can interpolate between rect and circle paths —
      // both use the same element structure (moveTo + 4×lineTo+curveTo + close).
      // ovalIn uses a different element count and snaps instead of morphing.
      let radius = min(cutRect.width, cutRect.height) / 2
      return UIBezierPath(roundedRect: cutRect, cornerRadius: radius)
    case .rect:
      return UIBezierPath(
        roundedRect: cutRect,
        // Keep > 0: a zero radius yields a different path element structure
        // (plain rect) that can't be interpolated with the rounded/circle one.
        cornerRadius: max(borderRadius + padding, 0.01)
      )
    }
  }

  private func makeOverlayPath(holePath: UIBezierPath?) -> UIBezierPath? {
    guard let holePath else { return nil }
    let path = UIBezierPath()
    path.usesEvenOddFillRule = true
    // Use the window rect converted to local space rather than self.bounds.
    // If Fabric hasn't set our frame yet when highlight fires, bounds is zero —
    // a zero outer rect makes the hole path the only filled region (inverted dim).
    // The window rect is always larger than any card, so evenOdd always gives
    // dim-everywhere-except-hole, regardless of layout timing.
    let outerRect = window.map { convert($0.bounds, from: nil) } ?? bounds
    path.append(UIBezierPath(rect: outerRect))
    path.append(holePath)
    return path
  }

  private func makeRingPath(holePath: UIBezierPath?) -> UIBezierPath? {
    holePath
  }


  // MARK: - Animation

  private var hasRunningPathAnimation: Bool {
    spotlightMask.animation(forKey: "path") != nil ||
    ringLayer.animation(forKey: "path") != nil ||
    spotlightMask.animation(forKey: Self.fadeKey) != nil
  }

  private func animate(
    layer: CAShapeLayer,
    from: CGPath?,
    to: CGPath?,
    duration: TimeInterval
  ) {
    let actualFrom = layer.presentation()?.path ?? from
    layer.removeAnimation(forKey: "path")
    layer.path = to
    guard let actualFrom, let to else { return }
    let anim = CABasicAnimation(keyPath: "path")
    anim.fromValue = actualFrom
    anim.toValue = to
    anim.duration = duration
    anim.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
    anim.isRemovedOnCompletion = true
    layer.add(anim, forKey: "path")
  }
}

private extension CGRect {
  func isApproximatelyEqual(
    to other: CGRect,
    tolerance: CGFloat = 0.5
  ) -> Bool {
    abs(origin.x - other.origin.x) <= tolerance &&
    abs(origin.y - other.origin.y) <= tolerance &&
    abs(size.width - other.size.width) <= tolerance &&
    abs(size.height - other.size.height) <= tolerance
  }
}

private extension UIColor {
  static func spotlightColor(from hexString: String) -> UIColor {
    var hex = hexString.trimmingCharacters(in: .whitespacesAndNewlines)
    if hex.hasPrefix("#") { hex.removeFirst() }
    var value: UInt64 = 0
    guard Scanner(string: hex).scanHexInt64(&value) else { return .white }
    switch hex.count {
    case 6:
      return UIColor(
        red: CGFloat((value & 0xFF0000) >> 16) / 255.0,
        green: CGFloat((value & 0x00FF00) >> 8) / 255.0,
        blue: CGFloat(value & 0x0000FF) / 255.0,
        alpha: 1.0
      )
    case 8:
      return UIColor(
        red: CGFloat((value & 0x00FF0000) >> 16) / 255.0,
        green: CGFloat((value & 0x0000FF00) >> 8) / 255.0,
        blue: CGFloat(value & 0x000000FF) / 255.0,
        alpha: CGFloat((value & 0xFF000000) >> 24) / 255.0
      )
    default:
      return .white
    }
  }
}
