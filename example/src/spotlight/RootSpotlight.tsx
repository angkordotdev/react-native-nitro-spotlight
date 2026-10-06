import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ComponentRef,
  type ReactNode,
  type RefObject,
} from 'react';
import { StyleSheet, Text, View } from 'react-native';
import {
  Spotlight,
  useSpotlight,
  type HighlightOptions,
} from 'react-native-nitro-spotlight';
import { Portal } from 'react-native-teleport';
import { SpotlightButton } from '../components/SpotlightButton';
import { TooltipCard } from '../components/TooltipCard';
import { spotlightProps, styles as shared } from '../theme/styles';

/**
 * A single app-wide spotlight, teleported to the root.
 *
 * Why: a <Spotlight> rendered inside a screen sits *below* the native
 * navigation header. Teleporting it into a root-level <PortalHost> (a sibling
 * after <NavigationContainer>) makes the dim cover the status bar and header
 * too, and lets every screen share one native overlay.
 *
 * Setup (see App.tsx):
 *
 *   <PortalProvider>
 *     <RootSpotlightProvider>
 *       <NavigationContainer>...</NavigationContainer>
 *     </RootSpotlightProvider>
 *     <PortalHost name={ROOT_SPOTLIGHT_HOST} style={absoluteFill} />
 *   </PortalProvider>
 *
 * Usage (any screen):
 *
 *   const spotlight = useRootSpotlight();
 *   spotlight.show(ref, { title: 'Hello', description: 'This is a button.' });
 *
 * Note: <Spotlight> itself still has no provider — this context is just an
 * example-level convenience built on useSpotlight().
 */

/** Must match the `name` of the root <PortalHost> in App.tsx. */
export const ROOT_SPOTLIGHT_HOST = 'spotlight-root';

/** Exit/collapse duration; holdThen() waits this long before running its callback. */
const EXIT_MS = 200;

export interface RootSpotlightTip {
  title: string;
  description: string;
  /** Optional extra button next to "Got it", e.g. "Next" to chain targets. */
  action?: { label: string; onPress: () => void };
}

export interface RootSpotlightApi {
  /** Highlight a view and show a tooltip next to it. */
  show(
    ref: RefObject<ComponentRef<typeof View> | null>,
    tip: RootSpotlightTip,
    options?: HighlightOptions
  ): void;
  /** Hide the spotlight. */
  clear(): void;
  /**
   * Collapse the cutout but keep the full-screen dim up, wait for that to
   * finish, then run `fn`. Use it right before navigating: no stale cutout is
   * left over the previous screen's layout, and the screen never blinks bright
   * between the two screens. The next screen calls show() to open a cutout
   * (see useSpotlightOnArrive); clear() dismisses the dim.
   */
  holdThen(fn: () => void): void;
}

const RootSpotlightContext = createContext<RootSpotlightApi | null>(null);

export function useRootSpotlight(): RootSpotlightApi {
  const api = useContext(RootSpotlightContext);
  if (!api) {
    throw new Error(
      'useRootSpotlight must be used inside <RootSpotlightProvider>'
    );
  }
  return api;
}

export function RootSpotlightProvider({ children }: { children: ReactNode }) {
  const spotlight = useSpotlight();
  const [tip, setTip] = useState<RootSpotlightTip | null>(null);

  // highlight/clear from useSpotlight() are stable, so `api` never changes
  // identity and screens don't re-render when the spotlight moves.
  const { highlight, clear } = spotlight;

  const show = useCallback<RootSpotlightApi['show']>(
    (ref, nextTip, options) => {
      setTip(nextTip);
      highlight(ref, options);
    },
    [highlight]
  );

  const { hold } = spotlight; // stable

  const holdThen = useCallback(
    (fn: () => void) => {
      setTip(null); // the tooltip goes away at once; the dim stays
      hold();
      // Let the cutout finish collapsing so `fn` runs against a plain dim.
      setTimeout(fn, EXIT_MS + 30);
    },
    [hold]
  );

  const api = useMemo<RootSpotlightApi>(
    () => ({ show, clear, holdThen }),
    [show, clear, holdThen]
  );

  return (
    <RootSpotlightContext.Provider value={api}>
      {children}

      {/* Mounted once, offscreen. The <Portal> teleports its content to the
          root <PortalHost> as soon as that host exists. */}
      <View style={local.offscreen}>
        <Portal hostName={ROOT_SPOTLIGHT_HOST} style={local.anchor}>
          <Spotlight
            controls={spotlight}
            {...spotlightProps}
            enteringAnimation="fade"
            exitAnimation="fade"
            exitDurationMs={EXIT_MS}
            onBackdropPress={clear}
          >
            {spotlight.targetRect && tip ? (
              <TooltipCard targetRect={spotlight.targetRect}>
                <View style={shared.tooltip}>
                  <Text style={shared.tooltipTitle}>{tip.title}</Text>
                  <Text style={shared.tooltipCopy}>{tip.description}</Text>
                  <View style={shared.tooltipActions}>
                    <SpotlightButton
                      label="Got it"
                      variant={tip.action ? 'ghost' : 'primary'}
                      onPress={clear}
                    />
                    {tip.action ? (
                      <SpotlightButton
                        label={tip.action.label}
                        onPress={tip.action.onPress}
                      />
                    ) : null}
                  </View>
                </View>
              </TooltipCard>
            ) : null}
          </Spotlight>
        </Portal>
      </View>
    </RootSpotlightContext.Provider>
  );
}

const local = StyleSheet.create({
  offscreen: { position: 'absolute', top: -9999 },
  anchor: { width: 1, height: 1 },
});
