import UIKit

enum SpotlightShape: String {
  case rect
  case circle
}

public final class SpotlightView: UIView {

  // MARK: - Config

  var dimOpacity: CGFloat = 0.55 {
    didSet {
      guard oldValue != dimOpacity else { return }
      redraw(animated: false)
    }
  }

  var borderRadius: CGFloat = 12 {
    didSet {
      guard oldValue != borderRadius else { return }
      redraw(animated: false)
    }
  }

  var padding: CGFloat = 6 {
    didSet {
      guard oldValue != padding else { return }
      redraw(animated: false)
    }
  }

  var borderWidth: CGFloat = 0 {
    didSet {
      guard oldValue != borderWidth else { return }
      redraw(animated: false)
    }
  }

  var borderColor: String = "#FFFFFF" {
    didSet {
      guard oldValue != borderColor else { return }
      resolvedBorderColor = UIColor.spotlightColor(from: borderColor)
      ringLayer.strokeColor = resolvedBorderColor.cgColor
      redraw(animated: false)
    }
  }

  var shape: SpotlightShape = .rect {
    didSet {
      guard oldValue != shape else { return }
      redraw(animated: false)
    }
  }

  var allowOverlayClick = false

  var onBackdropPress: (() -> Void)?


  // MARK: - Private

  private let spotlightMask = CAShapeLayer()
  private let ringLayer = CAShapeLayer()

  /// Rect of the cutout (highlighted target). Window-space coords from measureInWindow.
  private var sourceRect = CGRect.zero
  private var currentOverlayPath: UIBezierPath?
  private var currentHolePath: UIBezierPath?
  private var resolvedBorderColor = UIColor.white

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
    guard !hasRunningPathAnimation else { return }
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

  private func localRect(from rect: CGRect) -> CGRect {
    guard let window else { return rect }
    return window.convert(rect, to: self)
  }

  // MARK: - Drawing

  private func redraw(
    animated: Bool,
    duration: TimeInterval = 0.25
  ) {
    spotlightMask.fillColor = UIColor.black.withAlphaComponent(dimOpacity).cgColor

    ringLayer.isHidden = borderWidth <= 0
    ringLayer.strokeColor = resolvedBorderColor.cgColor
    ringLayer.lineWidth = borderWidth

    let nextHolePath = makeHolePath()
    let nextOverlayPath = makeOverlayPath(holePath: nextHolePath)
    let nextRingPath = makeRingPath(holePath: nextHolePath)

    let oldOverlayPath = currentOverlayPath
    currentOverlayPath = nextOverlayPath
    currentHolePath = nextHolePath

    if animated {
      animate(
        layer: spotlightMask,
        from: oldOverlayPath?.cgPath ?? spotlightMask.presentation()?.path ?? spotlightMask.path,
        to: nextOverlayPath?.cgPath,
        duration: duration
      )
      animate(
        layer: ringLayer,
        from: ringLayer.presentation()?.path ?? ringLayer.path,
        to: nextRingPath?.cgPath,
        duration: duration
      )
    } else {
      spotlightMask.removeAnimation(forKey: "path")
      spotlightMask.path = nextOverlayPath?.cgPath

      ringLayer.removeAnimation(forKey: "path")
      ringLayer.path = nextRingPath?.cgPath
    }
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
        cornerRadius: max(borderRadius + padding, 0)
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
    ringLayer.animation(forKey: "path") != nil
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
