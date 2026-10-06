import UIKit

// MARK: - HybridSpotlightView

class HybridSpotlightView: HybridSpotlightViewSpec {

  // MARK: - Nitro

  private let spotlightView = SpotlightView()

  var view: UIView {
    spotlightView
  }

  override init() {
    super.init()
  }

  func beforeUpdate() {
    spotlightView.dimOpacity = CGFloat(dimOpacity ?? Self.defaultDimOpacity)
    spotlightView.shape = SpotlightShape(rawValue: shape ?? "rect") ?? .rect
    spotlightView.borderRadius = CGFloat(cornerRadius ?? Self.defaultBorderRadius)
    spotlightView.borderColor = ringColor ?? Self.defaultBorderColor
    spotlightView.padding = CGFloat(cutoutPadding ?? Self.defaultPadding)
    spotlightView.borderWidth = CGFloat(ringWidth ?? Self.defaultBorderWidth)
    spotlightView.allowOverlayClick = allowOverlayClick ?? Self.defaultAllowOverlayClick
    applyTransitions()
  }

  func onDropView() {
    DispatchQueue.main.async { [weak self] in
      self?.spotlightView.clear(animated: false)
    }
  }

  // MARK: - Props

  var dimOpacity: Double? {
    didSet {
      guard oldValue != dimOpacity else { return }
      spotlightView.dimOpacity = CGFloat(dimOpacity ?? Self.defaultDimOpacity)
    }
  }

  var shape: String? {
    didSet {
      guard oldValue != shape else { return }
      spotlightView.shape = SpotlightShape(rawValue: shape ?? "rect") ?? .rect
    }
  }

  var cornerRadius: Double? {
    didSet {
      guard oldValue != cornerRadius else { return }
      spotlightView.borderRadius = CGFloat(cornerRadius ?? Self.defaultBorderRadius)
    }
  }

  var cutoutPadding: Double? {
    didSet {
      guard oldValue != cutoutPadding else { return }
      spotlightView.padding = CGFloat(cutoutPadding ?? Self.defaultPadding)
    }
  }

  var ringWidth: Double? {
    didSet {
      guard oldValue != ringWidth else { return }
      spotlightView.borderWidth = CGFloat(ringWidth ?? Self.defaultBorderWidth)
    }
  }

  var ringColor: String? {
    didSet {
      guard oldValue != ringColor else { return }
      spotlightView.borderColor = ringColor ?? Self.defaultBorderColor
    }
  }

  var enteringAnimation: String? {
    didSet {
      guard oldValue != enteringAnimation else { return }
      applyTransitions()
    }
  }

  var exitAnimation: String? {
    didSet {
      guard oldValue != exitAnimation else { return }
      applyTransitions()
    }
  }

  var exitDurationMs: Double? {
    didSet {
      guard oldValue != exitDurationMs else { return }
      applyTransitions()
    }
  }

  private func applyTransitions() {
    spotlightView.enteringAnimation =
      SpotlightTransition(rawValue: enteringAnimation ?? "") ?? .zoom
    spotlightView.exitAnimation =
      SpotlightTransition(rawValue: exitAnimation ?? "") ?? .zoom
    spotlightView.exitDuration = (exitDurationMs ?? Self.defaultExitDurationMs) / 1000.0
  }

  var allowOverlayClick: Bool? {
    didSet {
      guard oldValue != allowOverlayClick else { return }
      spotlightView.allowOverlayClick = allowOverlayClick ?? Self.defaultAllowOverlayClick
    }
  }

  var onTargetLayout: ((Rect) -> Void)?

  var onBackdropPress: (() -> Void)? {
    didSet {
      spotlightView.onBackdropPress = onBackdropPress
    }
  }

  // MARK: - Methods

  func highlight(
    x: Double,
    y: Double,
    width: Double,
    height: Double
  ) throws {
    guard width > 0, height > 0 else { return }
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      let windowRect = CGRect(x: x, y: y, width: width, height: height)
      spotlightView.setHighlight(windowRect, animated: false)
      onTargetLayout?(spotlightView.wrapperRect(fromWindowRect: windowRect))
    }
  }

  func highlightAnimated(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    durationMs: Double
  ) throws {
    guard width > 0, height > 0 else { return }
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      let windowRect = CGRect(x: x, y: y, width: width, height: height)
      spotlightView.setHighlight(
        windowRect,
        animated: true,
        duration: durationMs / 1000.0
      )
      onTargetLayout?(spotlightView.wrapperRect(fromWindowRect: windowRect))
    }
  }

  func holdDim() throws {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      spotlightView.holdDim(animated: true, duration: spotlightView.exitDuration)
    }
  }

  func clear() throws {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      spotlightView.clear(animated: true, duration: spotlightView.exitDuration)
    }
  }

  private static let defaultDimOpacity = 0.55
  private static let defaultShape = "rect"
  private static let defaultBorderRadius = 12.0
  private static let defaultPadding = 6.0
  private static let defaultBorderWidth = 1.5
  private static let defaultBorderColor = "#FFFFFF"
  private static let defaultAllowOverlayClick = false
  private static let defaultExitDurationMs = 200.0
}
