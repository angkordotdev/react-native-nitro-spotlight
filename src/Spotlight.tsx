import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  type ReactNode,
  type RefObject,
} from 'react';
import { StyleSheet, View, type ViewStyle } from 'react-native';
import { callback } from 'react-native-nitro-modules';
import type { Rect } from './Spotlight.nitro';
import { SpotlightView, type SpotlightRef } from './SpotlightView';
import type { SpotlightControls } from './useSpotlight';

/** Shape of the cutout hole. */
export type SpotlightShape = 'rect' | 'circle';

/**
 * Transition used when the spotlight appears or disappears.
 * - 'zoom': the cutout grows out of / collapses into the target's centre
 * - 'fade': the dim layer fades in / out with the cutout in place
 * - 'none': appears / disappears instantly
 *
 * Moving between targets while the spotlight is already showing always
 * animates the cutout between the two rects.
 */
export type SpotlightAnimation = 'zoom' | 'fade' | 'none';

/**
 * Values sent to native when a prop is omitted. Native uses the same defaults.
 *
 * <Spotlight> always passes a concrete value instead of `undefined`: when a JS
 * prop is removed, React Native sends `null` for it, and Nitro's optional
 * converters only accept `undefined` — the commit would throw
 * "SpotlightView.<prop>: Value is null, expected a ...".
 */
const DEFAULTS = {
  dimOpacity: 0.55,
  shape: 'rect',
  borderRadius: 12,
  padding: 6,
  borderWidth: 1.5,
  borderColor: '#FFFFFF',
  enteringAnimation: 'zoom',
  exitAnimation: 'zoom',
  exitDurationMs: 200,
  allowOverlayClick: false,
} as const;

export interface SpotlightComponentProps {
  /** Controls returned by useSpotlight(). Preferred for app code. */
  controls?: SpotlightControls;

  /** @deprecated Use controls instead. */
  spotlightRef?: RefObject<SpotlightRef | null>;

  /** Opacity of the dim overlay. Default 0.55. */
  dimOpacity?: number;

  /**
   * Shape of the cutout hole.
   * - 'rect' (default): rounded rectangle, respects borderRadius
   * - 'circle': fully rounded (pill/circle) cutout sized to the target rect, ignores borderRadius
   */
  shape?: SpotlightShape;

  /** Border radius of the cutout hole. Ignored when shape is 'circle'. Default 12. */
  borderRadius?: number;

  /** Padding around the target rect. Default 6. */
  padding?: number;

  /** Width of the border around the cutout. Set to 0 to remove it. Default 1.5. */
  borderWidth?: number;

  /** Color of the border around the cutout. Default '#FFFFFF'. */
  borderColor?: string;

  /**
   * Transition when the spotlight first appears (from idle). Default 'zoom'.
   * Its duration is the `durationMs` passed to highlight().
   */
  enteringAnimation?: SpotlightAnimation;

  /** Transition when the spotlight is cleared. Default 'zoom'. */
  exitAnimation?: SpotlightAnimation;

  /** Duration of the exit animation in milliseconds. Default 200. */
  exitDurationMs?: number;

  /** Whether backdrop taps should pass through to Pressables underneath. onBackdropPress still fires. */
  allowOverlayClick?: boolean;

  /** Called when the dimmed backdrop outside the cutout is tapped. */
  onBackdropPress?: () => void;

  /**
   * Called after native highlights a target.
   * Provides the cutout rect in window coordinates — use this to position a tooltip.
   * When using controls, prefer reading controls.targetRect instead.
   */
  onTargetLayout?: (rect: Rect) => void;

  /**
   * Content rendered above the dim overlay (e.g. a custom tooltip).
   * Children are siblings of SpotlightView in the React tree, so they
   * composite above the dim layer at higher z-order automatically.
   */
  children?: ReactNode;

  /** Additional style for the overlay. Usually not needed. */
  style?: ViewStyle;
}

/**
 * Spotlight
 *
 * Full-screen overlay that highlights a measured view with a native cutout.
 * Pair with useSpotlight() to drive it.
 *
 * Place children (e.g. a custom tooltip) inside to render them above the dim
 * layer without any extra z-index or hole-punching. Works as a regular React
 * Native view — can be wrapped in any portal library (e.g. react-native-teleport)
 * to render from anywhere in the component tree.
 *
 * @example
 * ```tsx
 * const spotlight = useSpotlight()
 *
 * return (
 *   <View style={{ flex: 1 }}>
 *     <YourContent />
 *     <Spotlight controls={spotlight} onBackdropPress={spotlight.clear}>
 *       {spotlight.targetRect && (
 *         <MyTooltip targetRect={spotlight.targetRect}>
 *           <Text>Here's a tip!</Text>
 *         </MyTooltip>
 *       )}
 *     </Spotlight>
 *   </View>
 * )
 * ```
 */
export function Spotlight({
  controls,
  spotlightRef,
  dimOpacity,
  shape,
  borderRadius,
  padding,
  borderWidth,
  borderColor,
  enteringAnimation,
  exitAnimation,
  exitDurationMs,
  allowOverlayClick,
  onBackdropPress,
  onTargetLayout,
  children,
  style,
}: SpotlightComponentProps) {
  // Stable refs so callback() wrappers below never change identity on re-render.
  const onTargetLayoutRef = useRef(onTargetLayout);
  const onBackdropPressRef = useRef(onBackdropPress);
  const controlsRef = useRef(controls);
  const spotlightRefRef = useRef(spotlightRef);
  useEffect(() => {
    onTargetLayoutRef.current = onTargetLayout;
    onBackdropPressRef.current = onBackdropPress;
    controlsRef.current = controls;
    spotlightRefRef.current = spotlightRef;
  });

  // Holds the SpotlightRef without triggering a re-render when it changes.
  const spotlightInstanceRef = useRef<SpotlightRef | null>(null);

  const hybridRef = useCallback((ref: SpotlightRef | null) => {
    spotlightInstanceRef.current = ref;
    const targetRef = controlsRef.current?._ref ?? spotlightRefRef.current;
    if (targetRef) targetRef.current = ref;
  }, []);

  const handleTargetLayout = useCallback((rect: Rect) => {
    controlsRef.current?._onTargetLayout(rect);
    onTargetLayoutRef.current?.(rect);
  }, []);

  const handleBackdropPress = useCallback(() => {
    onBackdropPressRef.current?.();
  }, []);

  // Re-wire the stored instance when the controls/spotlightRef prop changes.
  useEffect(() => {
    const targetRef = controls?._ref ?? spotlightRef;
    if (!targetRef) return;
    targetRef.current = spotlightInstanceRef.current;
    return () => {
      targetRef.current = null;
    };
  }, [controls, spotlightRef]);

  // Stable Nitro callback wrappers — created once since all three handlers have
  // empty useCallback deps and never change identity.
  const hybridRefCb = useMemo(() => callback(hybridRef), [hybridRef]);
  const onBackdropPressCb = useMemo(
    () => callback(handleBackdropPress),
    [handleBackdropPress]
  );
  const onTargetLayoutCb = useMemo(
    () => callback(handleTargetLayout),
    [handleTargetLayout]
  );

  return (
    <View style={[styles.overlay, style]} pointerEvents="box-none">
      <SpotlightView
        hybridRef={hybridRefCb}
        dimOpacity={dimOpacity ?? DEFAULTS.dimOpacity}
        shape={shape ?? DEFAULTS.shape}
        cornerRadius={borderRadius ?? DEFAULTS.borderRadius}
        cutoutPadding={padding ?? DEFAULTS.padding}
        ringWidth={borderWidth ?? DEFAULTS.borderWidth}
        ringColor={borderColor ?? DEFAULTS.borderColor}
        enteringAnimation={enteringAnimation ?? DEFAULTS.enteringAnimation}
        exitAnimation={exitAnimation ?? DEFAULTS.exitAnimation}
        exitDurationMs={exitDurationMs ?? DEFAULTS.exitDurationMs}
        allowOverlayClick={allowOverlayClick ?? DEFAULTS.allowOverlayClick}
        onBackdropPress={onBackdropPressCb}
        onTargetLayout={onTargetLayoutCb}
        pointerEvents="box-none"
        style={StyleSheet.absoluteFillObject}
      />
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    zIndex: 2147483647,
  },
});
