import { useRef, type ElementRef } from 'react';
import { Text, View } from 'react-native';
import { Spotlight, useSpotlight } from 'react-native-nitro-spotlight';
import { TooltipCard } from '../components/TooltipCard';
import { FullWindowOverlay } from 'react-native-screens';
import { ScreenShell } from '../components/ScreenShell';
import { SpotlightButton } from '../components/SpotlightButton';
import { spotlightProps, styles } from '../theme/styles';

export function FullWindowScreen() {
  const spotlight = useSpotlight();
  const targetRef = useRef<ElementRef<typeof View>>(null);

  return (
    <ScreenShell
      title="FullWindowOverlay"
      copy="react-native-screens FullWindowOverlay renders above the native navigation bar on iOS. On Android it is a plain View: the dim still shows, but backdrop taps are not blocked — use the Teleport host screen there."
    >
      <View ref={targetRef} style={styles.card}>
        <Text style={styles.cardLabel}>Full window</Text>
        <Text style={styles.cardTitle}>Nav bar gets dimmed too</Text>
        <Text style={styles.cardCopy}>
          Wrap Spotlight in FullWindowOverlay and the overlay escapes the
          navigation stack — no portal library needed.
        </Text>
      </View>

      <View style={styles.actions}>
        <SpotlightButton
          label="Highlight"
          onPress={() => spotlight.highlight(targetRef, { durationMs: 420 })}
        />
        <SpotlightButton
          label="Clear"
          variant="secondary"
          onPress={spotlight.clear}
        />
      </View>

      <FullWindowOverlay>
        <Spotlight
          controls={spotlight}
          {...spotlightProps}
          enteringAnimation="fade"
          exitAnimation="fade"
          exitDurationMs={300}
          onBackdropPress={spotlight.clear}
        >
          {spotlight.targetRect ? (
            <TooltipCard targetRect={spotlight.targetRect}>
              <View style={styles.tooltip}>
                <Text style={styles.tooltipTitle}>Full window</Text>
                <Text style={styles.tooltipCopy}>
                  The overlay and tooltip sit above the navigation bar via
                  FullWindowOverlay — no extra portal setup required.
                </Text>
                <View style={styles.tooltipActions}>
                  <SpotlightButton label="Got it" onPress={spotlight.clear} />
                </View>
              </View>
            </TooltipCard>
          ) : null}
        </Spotlight>
      </FullWindowOverlay>
    </ScreenShell>
  );
}
