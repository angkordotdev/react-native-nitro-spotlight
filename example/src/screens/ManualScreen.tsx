import { useRef, useState, type ElementRef } from 'react';
import { Text, View } from 'react-native';
import { Spotlight, useSpotlight } from 'react-native-nitro-spotlight';
import { TooltipCard } from '../components/TooltipCard';
import { DemoTargets } from '../components/DemoTargets';
import { ScreenShell } from '../components/ScreenShell';
import { SpotlightButton } from '../components/SpotlightButton';
import { spotlightProps, styles } from '../theme/styles';

const TOOLTIP_COPY = {
  intro: {
    title: 'Intro card',
    copy: 'Pass any measured ref to spotlight.highlight().',
  },
  feature: {
    title: 'Feature card',
    copy: 'The overlay animates smoothly between any two rects.',
  },
  clear: {
    title: 'Clear card',
    copy: 'Tap "Got it" or the backdrop to dismiss.',
  },
} as const;

type TargetKey = keyof typeof TOOLTIP_COPY;

export function ManualScreen() {
  const spotlight = useSpotlight();
  const introRef = useRef<ElementRef<typeof View>>(null);
  const featureRef = useRef<ElementRef<typeof View>>(null);
  const clearRef = useRef<ElementRef<typeof View>>(null);
  const [activeKey, setActiveKey] = useState<TargetKey>('intro');

  return (
    <ScreenShell
      title="Manual highlight"
      copy="Pass any view ref to spotlight.highlight(ref). Repeated same-target taps are guarded to avoid animation hitching."
    >
      <DemoTargets
        introRef={introRef}
        featureRef={featureRef}
        clearRef={clearRef}
      />

      <View style={styles.actions}>
        <View style={styles.actionGrid}>
          <SpotlightButton
            label="Intro"
            onPress={() => {
              setActiveKey('intro');
              spotlight.highlight(introRef, { durationMs: 420 });
            }}
          />
          <SpotlightButton
            label="Feature"
            onPress={() => {
              setActiveKey('feature');
              spotlight.highlight(featureRef, { durationMs: 420 });
            }}
          />
          <SpotlightButton
            label="Clear card"
            onPress={() => {
              setActiveKey('clear');
              spotlight.highlight(clearRef, { durationMs: 420 });
            }}
          />
        </View>
        <SpotlightButton
          label="Clear spotlight"
          variant="secondary"
          onPress={spotlight.clear}
        />
      </View>

      <Spotlight
        controls={spotlight}
        {...spotlightProps}
        enteringAnimation="fade"
        exitAnimation="fade"
        onBackdropPress={spotlight.clear}
      >
        {spotlight.targetRect ? (
          <TooltipCard targetRect={spotlight.targetRect}>
            <View style={styles.tooltip}>
              <Text style={styles.tooltipTitle}>
                {TOOLTIP_COPY[activeKey].title}
              </Text>
              <Text style={styles.tooltipCopy}>
                {TOOLTIP_COPY[activeKey].copy}
              </Text>
              <View style={styles.tooltipActions}>
                <SpotlightButton label="Got it" onPress={spotlight.clear} />
              </View>
            </View>
          </TooltipCard>
        ) : null}
      </Spotlight>
    </ScreenShell>
  );
}
