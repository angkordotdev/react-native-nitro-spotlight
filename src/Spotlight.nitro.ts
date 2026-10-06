import type {
  HybridView,
  HybridViewProps,
  HybridViewMethods,
} from 'react-native-nitro-modules';

export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Note for direct <SpotlightView> users: Nitro's optional prop converters only
 * accept `undefined`, but React Native sends `null` when a prop is removed, which
 * throws at commit time. Pass explicit values (use <Spotlight>, which does) rather
 * than conditionally removing props.
 */
export interface SpotlightProps extends HybridViewProps {
  dimOpacity?: number;
  /** Shape of the cutout hole. 'rect' (default) or 'circle'. */
  shape?: string;
  // NOTE: the native-facing prop names below intentionally avoid `borderRadius`,
  // `padding`, `borderWidth` and `borderColor`. Those are standard ViewProps and
  // Fabric would apply them as Yoga layout (padding/border), collapsing the
  // overlay view's size. The public <Spotlight> props keep the familiar names.
  /** Border radius of the cutout hole. Ignored when shape is 'circle'. */
  cornerRadius?: number;
  /** Padding around the target rect. */
  cutoutPadding?: number;
  /** Width of the ring around the cutout. Set to 0 to remove it. */
  ringWidth?: number;
  /** Color of the ring around the cutout. */
  ringColor?: string;
  /** How the spotlight appears from idle: 'zoom' (default), 'fade' or 'none'. */
  enteringAnimation?: string;
  /** How the spotlight disappears on clear(): 'zoom' (default), 'fade' or 'none'. */
  exitAnimation?: string;
  /** Duration of the exit animation in ms. Default 200. */
  exitDurationMs?: number;
  /** Whether backdrop taps should pass through to Pressables underneath. onBackdropPress still fires. */
  allowOverlayClick?: boolean;
  // Called after native measures the target — JS uses this to position tooltip
  onTargetLayout?: (rect: Rect) => void;
  /** Called when the dimmed backdrop outside the cutout is tapped. */
  onBackdropPress?: () => void;
}

export interface SpotlightMethods extends HybridViewMethods {
  highlight(x: number, y: number, width: number, height: number): void;

  highlightAnimated(
    x: number,
    y: number,
    width: number,
    height: number,
    durationMs: number
  ): void;

  clear(): void;

  /**
   * Collapse the cutout but keep the full-screen dim (and touch blocking) up.
   * Use it between screens so the dim never blinks off. The next highlight*()
   * opens a cutout from the held dim; clear() fades the dim out.
   */
  holdDim(): void;
}

export type SpotlightView = HybridView<SpotlightProps, SpotlightMethods>;
