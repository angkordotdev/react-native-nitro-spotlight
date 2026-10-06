import { useRef, type ElementRef } from 'react';
import { Text, View } from 'react-native';
import { Spotlight, useSpotlight } from 'react-native-nitro-spotlight';
import { TooltipCard } from '../components/TooltipCard';
import { Portal } from 'react-native-teleport';
import { ScreenShell } from '../components/ScreenShell';
import { SpotlightButton } from '../components/SpotlightButton';
import { spotlightProps, styles } from '../theme/styles';

export function TeleportScreen() {
  const spotlight = useSpotlight();
  const targetRef = useRef<ElementRef<typeof View>>(null);

  return (
    <ScreenShell
      title="react-native-teleport"
      copy="Spotlight is pre-mounted offscreen in a Portal and rendered above the native header via a root-level PortalHost in App.tsx."
    >
      <View ref={targetRef} style={styles.card}>
        <Text style={styles.cardLabel}>Teleport</Text>
        <Text style={styles.cardTitle}>Re-parent the anchor</Text>
        <Text style={styles.cardCopy}>
          The overlay and tooltip are portaled to a PortalHost that sits above
          the navigation stack, so the dim covers the native header.
        </Text>
      </View>

      <View style={styles.actions}>
        <SpotlightButton
          label="Highlight teleported"
          onPress={() => spotlight.highlight(targetRef, { durationMs: 420 })}
        />
      </View>

      <View style={styles.offscreen}>
        <Portal hostName="spotlight-root" style={styles.portalAnchor}>
          <Spotlight
            controls={spotlight}
            {...spotlightProps}
            onBackdropPress={spotlight.clear}
            enteringAnimation="fade"
            // exitAnimation="fade"
          >
            {spotlight.targetRect ? (
              <TooltipCard targetRect={spotlight.targetRect}>
                <View style={styles.tooltip}>
                  <Text style={styles.tooltipTitle}>Teleported tooltip</Text>
                  <Text style={styles.tooltipCopy}>
                    This tooltip and overlay were teleported here from an
                    offscreen Portal via react-native-teleport.
                  </Text>
                  <View style={styles.tooltipActions}>
                    <SpotlightButton label="Got it" onPress={spotlight.clear} />
                  </View>
                </View>
              </TooltipCard>
            ) : null}
          </Spotlight>
        </Portal>
      </View>
    </ScreenShell>
  );
}
