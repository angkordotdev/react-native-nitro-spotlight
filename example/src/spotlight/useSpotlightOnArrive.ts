import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useRef, type ComponentRef, type RefObject } from 'react';
import type { View } from 'react-native';
import type { RootStackParamList } from '../navigation/types';
import { useRootSpotlight, type RootSpotlightTip } from './RootSpotlight';

/**
 * Open the root spotlight on a target of THIS screen once the screen has
 * finished arriving.
 *
 * Navigating while a cutout is up would leave it hanging over the previous
 * screen's layout. So the leaving screen collapses the cutout but keeps the
 * dim (no bright blink, touches stay blocked):
 *
 *   spotlight.holdThen(() => navigation.navigate('Next', { arrive: Date.now() }));
 *
 * `arrive` is any changing number. This hook then waits for the navigation
 * transition to end (so measureInWindow reads the final, settled position) and
 * calls `show()`, which opens a cutout from the held dim on this screen's
 * target.
 *
 * Fires exactly once per `arrive` value.
 *
 * @param arrive   changing token from the route params; falsy = do nothing
 * @param getTip   tooltip to show (read lazily, may close over fresh state)
 */
export function useSpotlightOnArrive(
  targetRef: RefObject<ComponentRef<typeof View> | null>,
  getTip: () => RootSpotlightTip,
  arrive: number | undefined
) {
  const navigation =
    useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const spotlight = useRootSpotlight();

  // Latest values, read at fire time, so the effect below only depends on
  // `arrive` and never re-subscribes when these change identity.
  const latest = useRef({ navigation, spotlight, targetRef, getTip });
  latest.current = { navigation, spotlight, targetRef, getTip };
  const handled = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (!arrive || handled.current === arrive) return;

    let done = false;
    const run = () => {
      if (done) return;
      done = true;
      handled.current = arrive;
      clearTimeout(fallback);
      unsubscribe();
      const { spotlight: s, targetRef: ref, getTip: tip } = latest.current;
      s.show(ref, tip(), { durationMs: 500 });
    };

    // native-stack emits `transitionEnd` when the push/pop animation settles.
    // Fall back to a timer in case this screen never receives one.
    const fallback = setTimeout(run, 900);
    const unsubscribe = latest.current.navigation.addListener(
      'transitionEnd',
      (e) => {
        if (!e.data.closing) run();
      }
    );

    return () => {
      done = true;
      clearTimeout(fallback);
      unsubscribe();
    };
  }, [arrive]);
}
