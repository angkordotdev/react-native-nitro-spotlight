import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Header } from '../components/Header';
import type { RootStackParamList } from '../navigation/types';
import { styles } from '../theme/styles';

const SCENARIOS: Array<{
  name: keyof RootStackParamList;
  title: string;
  icon: string;
  copy: string;
}> = [
  {
    name: 'Manual',
    title: 'Manual highlight',
    icon: '🎯',
    copy: 'Highlight any measured ref directly.',
  },
  {
    name: 'Tour',
    title: 'Product tour',
    icon: '🧭',
    copy: 'Run next/back/stop with useSpotlightTour.',
  },
  {
    name: 'CustomState',
    title: 'Custom state',
    icon: '🗃️',
    copy: 'Drive a tour from useState, zustand, jotai, or any store.',
  },
  {
    name: 'Touch',
    title: 'Touch behavior',
    icon: '👆',
    copy: 'Test allowOverlayClick and backdrop press.',
  },
  {
    name: 'Lifecycle',
    title: 'Unmount cleanup',
    icon: '🧼',
    copy: 'Unmount while highlighted and verify cleanup.',
  },
  {
    name: 'Teleport',
    title: 'Teleport host',
    icon: '🌀',
    copy: 'One app-wide spotlight teleported above the native header.',
  },
  {
    name: 'FullWindow',
    title: 'Full window overlay',
    icon: '🪟',
    copy: 'Dim the nav bar too with FullWindowOverlay from react-native-screens.',
  },
  {
    name: 'Shape',
    title: 'Circle cutout',
    icon: '⭕',
    copy: 'Use shape="circle" for FABs, avatars and round icon buttons.',
  },
  {
    name: 'Sheet',
    title: 'Form / bottom sheet',
    icon: '📋',
    copy: 'Spotlight inside a form sheet (iOS) or bottom sheet (Android) dialog window.',
  },
];

export function HomeScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'Home'>) {
  return (
    <ScrollView>
      <View style={styles.screen}>
        <View style={styles.content}>
          <Header />

          <View style={styles.scenarioList}>
            {SCENARIOS.map((scenario) => (
              <Pressable
                key={scenario.name}
                accessibilityRole="button"
                onPress={() => navigation.navigate(scenario.name)}
                style={({ pressed }) => [
                  styles.navCard,
                  pressed && styles.pressedButton,
                ]}
              >
                <Text style={styles.navIcon}>{scenario.icon}</Text>
                <View style={styles.navCopyWrap}>
                  <Text style={styles.navTitle}>{scenario.title}</Text>
                  <Text style={styles.navCopy}>{scenario.copy}</Text>
                </View>
                <Text style={styles.navArrow}>→</Text>
              </Pressable>
            ))}
          </View>
        </View>
      </View>
    </ScrollView>
  );
}
