import { useRef, useCallback, useEffect, useMemo, useState } from 'react';
import type { RefObject, ComponentRef } from 'react';
import { View } from 'react-native';
import type { Rect, SpotlightView } from './Spotlight.nitro';
import type { SpotlightRef } from './SpotlightView';

// Internal shared ref — <Spotlight> writes here, useSpotlight reads here.
type SpotlightInstance = RefObject<SpotlightRef | null>;

export interface HighlightOptions {
  /** Animation duration in ms. Default 300. */
  durationMs?: number;
}

export interface SpotlightControls {
  /** @internal — consumed by <Spotlight controls={...}>, not for direct use */
  _ref: SpotlightInstance;

  /** @internal — consumed by <Spotlight controls={...}> to pipe onTargetLayout back here */
  _onTargetLayout: (rect: Rect) => void;

  /**
   * Highlight a view by passing its ref.
   *
   * @example
   * spotlight.highlight(cardRef)
   * spotlight.highlight(cardRef, { durationMs: 500 })
   */
  highlight(
    viewRef: RefObject<ComponentRef<typeof View> | null>,
    options?: HighlightOptions
  ): void;

  /** Clear the spotlight. */
  clear(): void;

  /**
   * Collapse the cutout but keep the dim (and touch blocking) showing. Useful
   * between screens: hold(), navigate, then highlight() on the new screen so
   * the dim never blinks off. Call clear() to dismiss it.
   */
  hold(): void;

  /** Current cutout rect in window coordinates. null when the spotlight is hidden. */
  targetRect: Rect | null;
}

/**
 * useSpotlight
 *
 * @example
 * ```tsx
 * const spotlight = useSpotlight()
 *
 * return (
 *   <>
 *     <View ref={cardRef}>...</View>
 *     <Button onPress={() => spotlight.highlight(cardRef)} />
 *     <Spotlight controls={spotlight} />
 *   </>
 * )
 * ```
 */
export function useSpotlight(): SpotlightControls {
  const _ref = useRef<SpotlightView | null>(null);
  const animatingTargetRef = useRef<ComponentRef<typeof View> | null>(null);
  const animationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Bumped by every highlight()/clear() so measureInWindow callbacks and rAF
  // retries from a superseded call can detect they are stale and bail out.
  const generationRef = useRef(0);
  const activeRef = useRef(false);
  const [targetRect, setTargetRect] = useState<Rect | null>(null);

  const finishAnimationGuard = useCallback(() => {
    if (animationTimerRef.current) {
      clearTimeout(animationTimerRef.current);
    }

    animationTimerRef.current = null;
    animatingTargetRef.current = null;
  }, []);

  useEffect(
    () => () => {
      generationRef.current++;
      finishAnimationGuard();
    },
    [finishAnimationGuard]
  );

  const _onTargetLayout = useCallback((rect: Rect) => {
    // A late native callback after clear() must not resurrect the tooltip rect.
    if (!activeRef.current) return;
    setTargetRect(rect);
  }, []);

  const highlight = useCallback(
    (
      viewRef: RefObject<ComponentRef<typeof View> | null>,
      { durationMs = 300 }: HighlightOptions = {}
    ) => {
      const instance = _ref.current;
      const target = viewRef.current;
      if (!instance || !target) return;

      // Repeated taps on the same target can restart native path animations
      // against the same destination. Ignore duplicates until this animation
      // window completes, while still allowing taps on a different target.
      if (animatingTargetRef.current === target) return;

      finishAnimationGuard();
      animatingTargetRef.current = target;
      const generation = ++generationRef.current;
      activeRef.current = true;
      const isStale = () => generation !== generationRef.current;

      const animateToRect = (
        x: number,
        y: number,
        width: number,
        height: number
      ) => {
        if (isStale()) return;
        // Re-read the ref: <Spotlight> may have unmounted between highlight()
        // and the measureInWindow callback firing.
        const current = _ref.current;
        if (!current) {
          finishAnimationGuard();
          return;
        }
        current.highlightAnimated(x, y, width, height, durationMs);
        animationTimerRef.current = setTimeout(
          finishAnimationGuard,
          durationMs
        );
      };

      target.measureInWindow((x, y, width, height) => {
        if (isStale()) return;
        if (width === 0 && height === 0) {
          requestAnimationFrame(() => {
            if (isStale()) return;
            const retryTarget = viewRef.current;
            if (!retryTarget) {
              finishAnimationGuard();
              return;
            }

            retryTarget.measureInWindow((rx, ry, rw, rh) => {
              if (isStale()) return;
              if (rw === 0 && rh === 0) {
                finishAnimationGuard();
                return;
              }
              animateToRect(rx, ry, rw, rh);
            });
          });
          return;
        }

        animateToRect(x, y, width, height);
      });
    },
    [finishAnimationGuard]
  );

  const clear = useCallback(() => {
    generationRef.current++;
    activeRef.current = false;
    finishAnimationGuard();
    setTargetRect(null);
    _ref.current?.clear();
  }, [finishAnimationGuard]);

  const hold = useCallback(() => {
    generationRef.current++;
    activeRef.current = false;
    finishAnimationGuard();
    setTargetRect(null);
    _ref.current?.holdDim();
  }, [finishAnimationGuard]);

  return useMemo(
    () => ({ _ref, _onTargetLayout, highlight, clear, hold, targetRect }),
    [clear, hold, highlight, _onTargetLayout, targetRect]
  );
}
