import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useRef, type ElementRef } from 'react';
import { Text, View } from 'react-native';
import { ScreenShell } from '../components/ScreenShell';
import { SpotlightButton } from '../components/SpotlightButton';
import type { RootStackParamList } from '../navigation/types';
import { useRootSpotlight } from '../spotlight/RootSpotlight';
import { useSpotlightOnArrive } from '../spotlight/useSpotlightOnArrive';
import { styles } from '../theme/styles';

/**
 * Second screen using the very same root spotlight. The Teleport screen
 * collapses the cutout (keeping the dim) before navigating here; once this
 * screen's transition ends it opens a new cutout on the card below.
 */
export function TeleportDetailsScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'TeleportDetails'>) {
  const spotlight = useRootSpotlight();
  const targetRef = useRef<ElementRef<typeof View>>(null);

  const goBack = () =>
    spotlight.holdThen(() =>
      navigation.navigate('Teleport', { arrive: Date.now() })
    );

  useSpotlightOnArrive(
    targetRef,
    () => ({
      title: 'Arrived on a new screen',
      description:
        'The cutout collapsed before navigating (the dim stayed up), then opened here once the transition ended — no stale cutout, no bright flash.',
      action: { label: 'Back to start', onPress: goBack },
    }),
    route.params?.arrive
  );

  // Leaving by any other route (swipe back, hardware back) shouldn't leave a
  // cutout over the previous screen.
  useEffect(
    () => navigation.addListener('beforeRemove', () => spotlight.clear()),
    [navigation, spotlight]
  );

  return (
    <ScreenShell
      title="Another screen"
      copy="Same app-wide spotlight, different screen. No extra setup per screen."
    >
      <View ref={targetRef} style={styles.card}>
        <Text style={styles.cardLabel}>Details</Text>
        <Text style={styles.cardTitle}>Shared overlay</Text>
        <Text style={styles.cardCopy}>
          useRootSpotlight() works in any screen under the provider.
        </Text>
      </View>

      <View style={styles.actions}>
        <SpotlightButton
          label="Highlight"
          onPress={() =>
            spotlight.show(targetRef, {
              title: 'Works everywhere',
              description:
                'Highlight from any screen — the overlay always renders at the root.',
            })
          }
        />
      </View>
    </ScreenShell>
  );
}
