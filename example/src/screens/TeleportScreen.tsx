import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useRef, type ElementRef } from 'react';
import { Text, View } from 'react-native';
import { ScreenShell } from '../components/ScreenShell';
import { SpotlightButton } from '../components/SpotlightButton';
import type { RootStackParamList } from '../navigation/types';
import { useRootSpotlight } from '../spotlight/RootSpotlight';
import { useSpotlightOnArrive } from '../spotlight/useSpotlightOnArrive';
import { styles } from '../theme/styles';

/**
 * No <Spotlight> here — the screen just asks the app-wide, teleported
 * spotlight (RootSpotlight.tsx) to highlight one of its views.
 *
 * Flow: Start → step 1 → Next → step 2 (the cutout glides between targets on
 * this screen) → "Next screen" collapses the cutout (the dim stays up),
 * navigates, and the details screen opens a new cutout on its own target.
 * "Back to start" does the same in reverse.
 */
export function TeleportScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'Teleport'>) {
  const spotlight = useRootSpotlight();
  const cardRef = useRef<ElementRef<typeof View>>(null);
  const noteRef = useRef<ElementRef<typeof View>>(null);

  // Collapse the cutout (the dim stays), THEN navigate, so the old cutout
  // never hangs over the sliding screens and the screen never blinks bright.
  // TeleportDetails opens a new cutout on arrival.
  const goToDetails = () =>
    spotlight.holdThen(() =>
      navigation.navigate('TeleportDetails', { arrive: Date.now() })
    );

  // Step 2 is reached from step 1's tooltip ("Next"): showing a new target
  // while the spotlight is visible glides the cutout between the two.
  const showStep2 = () =>
    spotlight.show(
      noteRef,
      {
        title: 'Same overlay, new target',
        description:
          'The native view is reused, so moving between targets is a smooth animation.',
        action: { label: 'Next screen', onPress: goToDetails },
      },
      { durationMs: 420 }
    );

  const showStep1 = () =>
    spotlight.show(
      cardRef,
      {
        title: 'Root spotlight',
        description:
          'This overlay is teleported to a PortalHost above the navigation stack, so the header is dimmed too.',
        action: { label: 'Next', onPress: showStep2 },
      },
      { durationMs: 420 }
    );

  // Returning from the details screen: fade back in on step 1.
  useSpotlightOnArrive(
    cardRef,
    () => stepOneTip(showStep2),
    route.params?.arrive
  );

  return (
    <ScreenShell
      title="Teleport (root spotlight)"
      copy="One Spotlight lives at the app root, above the native header. Screens just call spotlight.show(ref, tip)."
    >
      <View ref={cardRef} style={styles.card}>
        <Text style={styles.cardLabel}>Step 1</Text>
        <Text style={styles.cardTitle}>Nothing to mount here</Text>
        <Text style={styles.cardCopy}>
          This screen has no Spotlight and no Portal. It only calls
          useRootSpotlight().
        </Text>
      </View>

      <View ref={noteRef} style={[styles.card, { marginTop: 14 }]}>
        <Text style={styles.cardLabel}>Step 2</Text>
        <Text style={styles.cardTitle}>Move between targets</Text>
        <Text style={styles.cardCopy}>
          Tap Start, then Next in the tooltip. The last step collapses the
          cutout, keeps the dim, navigates, and opens a new cutout there.
        </Text>
      </View>

      <View style={styles.actions}>
        <SpotlightButton label="Start" onPress={showStep1} />
        <SpotlightButton
          label="Open a second screen"
          variant="secondary"
          onPress={() => {
            spotlight.clear();
            navigation.navigate('TeleportDetails');
          }}
        />
      </View>
    </ScreenShell>
  );
}

function stepOneTip(next: () => void) {
  return {
    title: 'Back on the first screen',
    description:
      'The cutout collapsed (the dim stayed) before navigating, then opened here once this screen settled.',
    action: { label: 'Next', onPress: next },
  };
}
