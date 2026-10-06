# react-native-nitro-spotlight ✨

> This is a vibe project — built for playful product tours, polished onboarding, and UI moments that feel alive.

A tiny native spotlight overlay for React Native. Dim the whole screen, cut a buttery hole around any view, and build product tours that feel clean instead of clunky.

Powered by [Nitro Modules](https://nitro.margelo.com/). Built for the New Architecture.

```tsx
<Spotlight controls={spotlight} />
```

## Why it slaps

- 🎯 Highlight any React Native view by ref
- 🪄 Smooth native cutout animations
- 🧭 Built-in multi-step onboarding tours
- 👆 Touches inside the cutout pass through by default
- 🌑 Custom dim opacity, radius, padding, border width, and border color
- ⚡ Nitro-powered native view, no heavy JS overlay games

## Requirements

- React Native New Architecture
- [`react-native-nitro-modules`](https://nitro.margelo.com/)

## Installation

```sh
npm install react-native-nitro-spotlight react-native-nitro-modules
```

or

```sh
yarn add react-native-nitro-spotlight react-native-nitro-modules
```

## Quick start

Create spotlight controls, attach a ref to the thing you want to highlight, then render `<Spotlight />` once near the root of the screen.

```tsx
import { useRef, type ComponentRef } from 'react';
import { Button, Text, View } from 'react-native';
import { Spotlight, useSpotlight } from 'react-native-nitro-spotlight';

export function Example() {
  const spotlight = useSpotlight();
  const cardRef = useRef<ComponentRef<typeof View>>(null);

  return (
    <View style={{ flex: 1, padding: 24 }}>
      <View ref={cardRef} style={{ padding: 20, borderRadius: 16 }}>
        <Text>Main character energy</Text>
      </View>

      <Button
        title="Highlight card"
        onPress={() => spotlight.highlight(cardRef, { durationMs: 400 })}
      />

      <Button title="Clear" onPress={spotlight.clear} />

      <Spotlight
        controls={spotlight}
        dimOpacity={0.68}
        borderRadius={22}
        padding={8}
        borderColor="#FFFFFF"
      />
    </View>
  );
}
```

That’s it. No measuring. No provider. No portal juggling. No chaos.

## No provider needed

`react-native-nitro-spotlight` is intentionally local-first:

- call `useSpotlight()` inside the screen/component that owns the spotlight
- pass the returned controls to `<Spotlight controls={spotlight} />`
- call `spotlight.highlight(ref)` from any button or callback in that same scope

You do **not** need to wrap your app in a provider.

```tsx
function Screen() {
  const spotlight = useSpotlight();
  const targetRef = useRef<ComponentRef<typeof View>>(null);

  return (
    <View style={{ flex: 1 }}>
      <View ref={targetRef} />
      <Button title="Show" onPress={() => spotlight.highlight(targetRef)} />
      <Spotlight controls={spotlight} />
    </View>
  );
}
```

For multi-step flows, use `useSpotlightTour()` instead of a provider. The tour hook keeps its own target map and exposes `getTargetProps(id)`.

## Using with react-native-teleport 🌀

You usually do **not** need Teleport. `<Spotlight />` mounts its native overlay for you.

Use [`react-native-teleport`](https://github.com/kirillzyusko/react-native-teleport) when you want **one app-wide spotlight** that:

- **covers the native navigation header** — the dim goes truly full-screen, including the status bar and header bar
- is **shared by every screen** — screens don't mount their own `<Spotlight />`
- is **pre-mounted** — the same native view is reused across screens

> Spotlight itself has no provider. The `PortalProvider` below belongs to Teleport, and the small context in step 1 is plain app code built on `useSpotlight()`.

```sh
npm install react-native-teleport
```

The complete working version of this guide lives in the example app: [`example/src/spotlight/RootSpotlight.tsx`](example/src/spotlight/RootSpotlight.tsx), [`example/src/App.tsx`](example/src/App.tsx) and [`example/src/screens/TeleportScreen.tsx`](example/src/screens/TeleportScreen.tsx).

### 1. Create the root spotlight (once)

Own the controls in one component, render `<Spotlight />` inside a `Portal`, and expose a tiny `show(ref, tip)` API through context. Teleport keeps the portal content offscreen until a matching `PortalHost` mounts.

```tsx
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
import { Portal } from 'react-native-teleport';
import { Spotlight, useSpotlight } from 'react-native-nitro-spotlight';

export const ROOT_SPOTLIGHT_HOST = 'spotlight-root';

type Tip = { title: string; description: string };
type RootSpotlightApi = {
  show: (ref: RefObject<ComponentRef<typeof View> | null>, tip: Tip) => void;
  clear: () => void;
};

const RootSpotlightContext = createContext<RootSpotlightApi | null>(null);

export function useRootSpotlight() {
  const api = useContext(RootSpotlightContext);
  if (!api) throw new Error('Wrap your app in <RootSpotlightProvider>');
  return api;
}

export function RootSpotlightProvider({ children }: { children: ReactNode }) {
  const spotlight = useSpotlight();
  const [tip, setTip] = useState<Tip | null>(null);
  const { highlight, clear } = spotlight; // stable callbacks

  const show = useCallback<RootSpotlightApi['show']>(
    (ref, nextTip) => {
      setTip(nextTip);
      highlight(ref, { durationMs: 400 });
    },
    [highlight]
  );
  const api = useMemo(() => ({ show, clear }), [show, clear]);

  return (
    <RootSpotlightContext.Provider value={api}>
      {children}

      <View style={styles.offscreen}>
        <Portal hostName={ROOT_SPOTLIGHT_HOST} style={styles.anchor}>
          <Spotlight
            controls={spotlight}
            dimOpacity={0.68}
            borderRadius={22}
            padding={8}
            onBackdropPress={clear}
          >
            {spotlight.targetRect && tip ? (
              // Position your own tooltip with spotlight.targetRect.
              <MyTooltip rect={spotlight.targetRect} onDone={clear}>
                <Text>{tip.title}</Text>
                <Text>{tip.description}</Text>
              </MyTooltip>
            ) : null}
          </Spotlight>
        </Portal>
      </View>
    </RootSpotlightContext.Provider>
  );
}

const styles = StyleSheet.create({
  offscreen: { position: 'absolute', top: -9999 },
  anchor: { width: 1, height: 1 },
});
```

### 2. Mount it once at the app root

Put `RootSpotlightProvider` around your navigator, and a `PortalHost` **after** `NavigationContainer` (as a sibling) so it renders above the native header in z-order. That is what lets the dim cover the navigation bar.

```tsx
import { StyleSheet } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { PortalHost, PortalProvider } from 'react-native-teleport';
import { ROOT_SPOTLIGHT_HOST, RootSpotlightProvider } from './RootSpotlight';

export default function App() {
  return (
    <PortalProvider>
      <RootSpotlightProvider>
        <NavigationContainer>{/* your navigator */}</NavigationContainer>
      </RootSpotlightProvider>

      {/* Sibling AFTER NavigationContainer → renders above the native header */}
      <PortalHost name={ROOT_SPOTLIGHT_HOST} style={styles.host} />
    </PortalProvider>
  );
}

const styles = StyleSheet.create({
  host: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 },
});
```

### 3. Use it from any screen

No `<Spotlight />`, no `Portal`, no host in the screen — just ask the root spotlight to highlight a view.

```tsx
import { useRef, type ComponentRef } from 'react';
import { Button, Text, View } from 'react-native';
import { useRootSpotlight } from './RootSpotlight';

export function DetailsScreen() {
  const spotlight = useRootSpotlight();
  const actionRef = useRef<ComponentRef<typeof View>>(null);

  return (
    <View style={{ flex: 1, padding: 24 }}>
      <View ref={actionRef}>
        <Text>Primary action</Text>
      </View>

      <Button
        title="Show me"
        onPress={() =>
          spotlight.show(actionRef, {
            title: 'Primary action',
            description: 'Tap this to continue.',
          })
        }
      />
    </View>
  );
}
```

### 4. Navigate between screens (optional)

Navigating while the spotlight is showing leaves its **old cutout hanging over the previous screen's layout** while the new screen slides in. Clearing it first avoids that but makes the screen blink bright between the two. Use `hold()` instead: it collapses the cutout but **keeps the full-screen dim (and touch blocking) up**, then the next screen opens a new cutout.

1. On screen A, hold the dim, wait for the cutout to collapse, then navigate with a changing token:

   ```tsx
   // holdThen(fn): spotlight.hold() + run fn after the collapse — add it to your root API
   spotlight.holdThen(() =>
     navigation.navigate('Details', { arrive: Date.now() })
   );
   ```

2. On screen B, wait for the transition to finish (so `measureInWindow` reads the settled position), then `show()` — a cutout opens out of the held dim on B's target:

   ```tsx
   useSpotlightOnArrive(
     targetRef,
     () => ({ title: 'Welcome', description: '…' }),
     route.params?.arrive
   );
   ```

   The hook listens for `transitionEnd` (with a short timer fallback) and fires once per `arrive` value. See [`useSpotlightOnArrive.ts`](example/src/spotlight/useSpotlightOnArrive.ts) and the Teleport screens in the example for the full flow, including going back.

`hold()` is a normal control on `useSpotlight()` (`spotlight.hold()`); call `spotlight.clear()` to dismiss a held dim. Moving between targets on the **same** screen still glides the cutout.

How it works:

- App startup: the `<Spotlight />` mounts offscreen inside `Portal`, then Teleport moves it to the root `PortalHost`.
- Any screen calls `spotlight.show(ref, tip)` — the overlay appears above the native header.
- Target refs stay on the real views. `highlight(ref)` uses `measureInWindow`, so it works anywhere in the tree.
- Switching the shown target while the spotlight is visible animates the cutout between the two.

Notes:

- If you do not need full-screen coverage or sharing, render `<Spotlight controls={spotlight} />` directly in the screen instead.
- On Android, `FullWindowOverlay` from `react-native-screens` is a plain view (iOS only), so use the Teleport approach above when you need the header dimmed and backdrop taps blocked there.

## Building a tooltip

`Spotlight` has no built-in tooltip component — bring your own. Read `controls.targetRect` to position it. Render it as a child of `<Spotlight>` so it naturally composites above the native dim layer.

```tsx
import { useRef, type ComponentRef } from 'react';
import { Pressable, StyleSheet, useWindowDimensions, View, Text, Button } from 'react-native';
import { Spotlight, useSpotlight, type Rect } from 'react-native-nitro-spotlight';

// Minimal positioned tooltip — drop this into your own component
function MyTooltip({ targetRect, onDismiss }: { targetRect: Rect; onDismiss: () => void }) {
  const { width, height } = useWindowDimensions();
  const gap = 12;
  const margin = 16;
  const maxWidth = width - margin * 2;
  const left = Math.max(margin, Math.min(
    targetRect.x + targetRect.width / 2 - maxWidth / 2,
    width - maxWidth - margin
  ));
  const placeBelow = height - (targetRect.y + targetRect.height) >= targetRect.y;

  return (
    <View
      style={[
        styles.tooltip,
        placeBelow
          ? { top: targetRect.y + targetRect.height + gap, left, maxWidth }
          : { bottom: height - targetRect.y + gap, left, maxWidth },
      ]}
      pointerEvents="box-none"
    >
      <Pressable style={styles.card}>
        <Text style={styles.tip}>Here's a tip!</Text>
        <Button title="Got it" onPress={onDismiss} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  tooltip: { position: 'absolute' },
  card: { padding: 16, backgroundColor: '#1B2440', borderRadius: 12 },
  tip: { color: '#fff', marginBottom: 8 },
});

// Usage
function Example() {
  const spotlight = useSpotlight();
  const cardRef = useRef<ComponentRef<typeof View>>(null);

  return (
    <View style={{ flex: 1 }}>
      <View ref={cardRef}><Text>Target</Text></View>
      <Button onPress={() => spotlight.highlight(cardRef)} title="Show" />

      <Spotlight controls={spotlight} onBackdropPress={spotlight.clear}>
        {spotlight.targetRect && (
          <MyTooltip targetRect={spotlight.targetRect} onDismiss={spotlight.clear} />
        )}
      </Spotlight>
    </View>
  );
}
```

**To add animation** — wrap in a Reanimated `Animated.View` with `entering`/`exiting`, or drive a `useRef(new Animated.Value(0))` from a `useEffect` that watches `targetRect`. Use a `key` derived from the rect coordinates to re-trigger `entering` on each tour step change:

```tsx
import Animated, { FadeIn, FadeOut } from 'react-native-reanimated';

// inside <Spotlight>:
{spotlight.targetRect && (
  <Animated.View
    key={`${spotlight.targetRect.x}-${spotlight.targetRect.y}`}
    entering={FadeIn.delay(180).duration(150)}
    exiting={FadeOut.duration(100)}
  >
    <MyTooltip targetRect={spotlight.targetRect} onDismiss={spotlight.clear} />
  </Animated.View>
)}
```

The `delay(180)` lets the cutout animation travel most of the way before the tooltip appears. Tune it to match your `durationMs` on `highlight()`.

## Product tour mode 🧭

Use `useSpotlightTour()` when you want a real walkthrough.

Each step has an `id`. Spread `getTargetProps(id)` on the matching view, then call `tour.start()`.

```tsx
import { useMemo } from 'react';
import { Button, Text, View } from 'react-native';
import { Spotlight, useSpotlightTour } from 'react-native-nitro-spotlight';

export function TutorialExample() {
  const steps = useMemo(
    () => [
      {
        id: 'filter',
        title: 'Filter stuff',
        description: 'Use this to find exactly what you need.',
      },
      {
        id: 'item-1',
        title: 'Open an item',
        description: 'Tap a result to see the details.',
      },
      {
        id: 'save',
        title: 'Save it',
        description: 'Keep your favorites for later.',
      },
    ],
    []
  );

  const tour = useSpotlightTour({ steps });

  return (
    <View style={{ flex: 1, padding: 24 }}>
      <View {...tour.getTargetProps('filter')}>
        <Text>Filter</Text>
      </View>

      <View {...tour.getTargetProps('item-1')}>
        <Text>Item 1</Text>
      </View>

      <View {...tour.getTargetProps('save')}>
        <Text>Save</Text>
      </View>

      <Button title="Start tour" onPress={() => tour.start()} />

      {tour.currentStep && (
        <View style={{ marginTop: 'auto', padding: 16 }}>
          <Text>{tour.currentStep.title}</Text>
          <Text>{tour.currentStep.description}</Text>
          <Button title="Next" onPress={tour.next} />
        </View>
      )}

      <Spotlight
        controls={tour.spotlight}
        dimOpacity={0.68}
        borderRadius={20}
        padding={8}
        borderColor="#FFFFFF"
        onBackdropPress={tour.stop}
      />
    </View>
  );
}
```

Jump around whenever you need:

```tsx
tour.start('filter'); // start at a specific step
tour.goTo('item-1'); // jump by id
tour.goTo(2); // jump by index
tour.previous(); // go back
tour.stop(); // end the tour
```

> Keep your `steps` stable with `useMemo`, and start the tour only after target views have mounted.

## Touch behavior

The default behavior is usually what you want:

- touches **inside** the cutout pass through to your app
- touches on the dimmed backdrop are blocked
- `onBackdropPress` fires when the backdrop is tapped

`allowOverlayClick` means “let the user click buttons under the dim overlay.” It does **not** disable `onBackdropPress` — the callback still fires when the backdrop is tapped.

Remove the ring:

```tsx
<Spotlight controls={spotlight} borderWidth={0} />
```

Let the dimmed backdrop pass touches through to buttons underneath:

```tsx
<Spotlight
  controls={spotlight}
  allowOverlayClick
  onBackdropPress={() => console.log('Backdrop tapped, but touch still passes through')}
/>
```

Close on backdrop tap:

```tsx
<Spotlight controls={spotlight} onBackdropPress={spotlight.clear} />
```

## Bring your own state

`useSpotlightTour` manages step state internally. If you want to drive a tour from **zustand, jotai, redux, context, or any other store**, use `useSpotlightTargets` instead. It handles only the id → native View mapping; all step state lives wherever you put it.

```tsx
import { useEffect } from 'react';
import { View, Button, Text } from 'react-native';
import { Spotlight, useSpotlight, useSpotlightTargets } from 'react-native-nitro-spotlight';
```

### With zustand

```tsx
import { create } from 'zustand';

const STEPS = ['intro', 'feature', 'done'] as const;
type StepId = typeof STEPS[number];

const useTourStore = create<{
  step: StepId | null;
  setStep: (step: StepId | null) => void;
}>((set) => ({
  step: null,
  setStep: (step) => set({ step }),
}));

export function TourScreen() {
  const spotlight = useSpotlight();
  const targets = useSpotlightTargets(spotlight);
  const { step, setStep } = useTourStore();

  useEffect(() => {
    if (step) targets.highlightById(step, { durationMs: 350 });
    else spotlight.clear();
  }, [step]);

  return (
    <View style={{ flex: 1, padding: 24 }}>
      <View {...targets.getTargetProps('intro')}>
        <Text>Intro</Text>
      </View>
      <View {...targets.getTargetProps('feature')}>
        <Text>Feature</Text>
      </View>
      <View {...targets.getTargetProps('done')}>
        <Text>Done</Text>
      </View>

      <Button title="Start" onPress={() => setStep('intro')} />

      {step && (
        <View style={{ marginTop: 'auto', padding: 16 }}>
          <Text>Step: {step}</Text>
          <Button
            title="Next"
            onPress={() => {
              const i = STEPS.indexOf(step);
              setStep(i + 1 < STEPS.length ? STEPS[i + 1] : null);
            }}
          />
        </View>
      )}

      <Spotlight
        controls={spotlight}
        dimOpacity={0.68}
        borderRadius={20}
        padding={8}
        onBackdropPress={() => setStep(null)}
      />
    </View>
  );
}
```

### With jotai

```tsx
import { atom, useAtom } from 'jotai';

const stepAtom = atom<string | null>(null);

export function TourScreen() {
  const spotlight = useSpotlight();
  const targets = useSpotlightTargets(spotlight);
  const [step, setStep] = useAtom(stepAtom);

  useEffect(() => {
    if (step) targets.highlightById(step, { durationMs: 350 });
    else spotlight.clear();
  }, [step]);

  // ... same JSX as above
}
```

### With React context

```tsx
const TourContext = createContext<{
  step: string | null;
  setStep: (s: string | null) => void;
} | null>(null);

export function TourScreen() {
  const spotlight = useSpotlight();
  const targets = useSpotlightTargets(spotlight);
  const { step, setStep } = useContext(TourContext)!;

  useEffect(() => {
    if (step) targets.highlightById(step, { durationMs: 350 });
    else spotlight.clear();
  }, [step]);

  // ... same JSX as above
}
```

The rule: `useSpotlight()` owns the native layer. `useSpotlightTargets(spotlight)` owns the id→ref map. Your store owns step state. Spread `getTargetProps(id)` on each target view, call `highlightById(id)` when your state changes.

## API

### `useSpotlight()`

```tsx
const spotlight = useSpotlight();
```

| Field | Type | What it does |
| --- | --- | --- |
| `highlight` | `(viewRef, options?) => void` | Measures a view ref and animates the cutout to it. |
| `clear` | `() => void` | Hides the overlay. |
| `hold` | `() => void` | Collapses the cutout but keeps the full-screen dim (and touch blocking) up — e.g. across a screen change. The next `highlight()` opens a cutout from it; `clear()` dismisses it. |
| `targetRect` | `Rect \| null` | Current cutout rect in overlay-local coordinates. `null` when hidden. Use this to position your own tooltip above the dim layer. |
| `_ref` | `RefObject` | Internal native ref. Use `<Spotlight controls={spotlight} />` instead of touching this directly. |

### `highlight(viewRef, options?)`

| Option | Type | Default | What it does |
| --- | --- | --- | --- |
| `durationMs` | `number` | `300` | Animation duration in milliseconds. |

### `<Spotlight />`

Render one per screen or flow.

| Prop | Type | What it does |
| --- | --- | --- |
| `controls` | `SpotlightControls` | Controls from `useSpotlight()` or `tour.spotlight`. |
| `dimOpacity` | `number` | Opacity of the dim overlay. |
| `borderRadius` | `number` | Radius of the cutout. |
| `padding` | `number` | Extra space around the highlighted view. |
| `borderWidth` | `number` | Width of the cutout ring. Use `0` to hide it. |
| `borderColor` | `string` | Ring color. Hex strings like `"#FFFFFF"` are supported. |
| `enteringAnimation` | `'zoom' \| 'fade' \| 'none'` | How the spotlight appears from idle. Default `'zoom'` (cutout grows out of the target). `'fade'` fades the dim in with the cutout in place. Duration is the `durationMs` passed to `highlight()`. |
| `exitAnimation` | `'zoom' \| 'fade' \| 'none'` | How the spotlight disappears on `clear()`. Default `'zoom'` (cutout collapses into its centre). `'fade'` fades the dim out in place. |
| `exitDurationMs` | `number` | Duration of the exit animation. Default `200`. |
| `allowOverlayClick` | `boolean` | Lets backdrop touches pass through to views/buttons underneath. `onBackdropPress` still fires. |
| `onBackdropPress` | `() => void` | Called when the backdrop outside the cutout is tapped. |
| `style` | `ViewStyle` | Style for the zero-size native anchor. Usually not needed. |
| `spotlightRef` | `RefObject<SpotlightRef \| null>` | Deprecated escape hatch. Prefer `controls`. |

Moving from one target to another while the spotlight is showing always animates the cutout between the two rects; `enteringAnimation` / `exitAnimation` only apply when it appears from idle or is cleared.

```tsx
<Spotlight controls={spotlight} enteringAnimation="fade" exitAnimation="fade" />
```

### `useSpotlightTour({ steps })`

```tsx
const tour = useSpotlightTour({ steps });
```

| Field | Type | What it does |
| --- | --- | --- |
| `spotlight` | `SpotlightControls` | Pass this to `<Spotlight controls={tour.spotlight} />`. |
| `steps` | `SpotlightTourStep[]` | Your tour config. |
| `currentStep` | `SpotlightTourStep \| null` | Active step, or `null` when idle. |
| `currentIndex` | `number` | Active step index, or `-1` when idle. |
| `isActive` | `boolean` | Whether the tour is currently active. |
| `getTargetProps` | `(id: string) => { ref, collapsable: false }` | Spread on the target view for that step. |
| `start` | `(idOrIndex?: string \| number) => void` | Start the tour. Defaults to the first step. |
| `goTo` | `(idOrIndex: string \| number) => void` | Jump to a step. |
| `next` | `() => void` | Move forward. Stops at the end. |
| `previous` | `() => void` | Move back one step. |
| `stop` | `() => void` | Clear the spotlight and end the tour. |

Step shape:

```ts
type SpotlightTourStep = {
  id: string;
  title?: string;
  description?: string;
  durationMs?: number;
};
```

### `useSpotlightTargets(spotlight)`

```tsx
const targets = useSpotlightTargets(spotlight);
```

The ref-registration half of `useSpotlightTour`, without any step state. Use this when you want to drive a tour from your own state management (zustand, jotai, redux, context, etc.).

| Field | Type | What it does |
| --- | --- | --- |
| `getTargetProps` | `(id: string) => { ref, collapsable: false }` | Spread on the target view for that step. |
| `highlightById` | `(id: string, options?) => void` | Highlight the registered view for a given id. No-op if the id is not yet registered. |

### `SpotlightView`

Low-level native view export for custom wiring.

Most apps should use:

```tsx
<Spotlight controls={spotlight} />
```

Only reach for `SpotlightView` if you need direct native ref control.

```tsx
import { SpotlightView } from 'react-native-nitro-spotlight';
```

## Demo

<table align="center">
  <tr>
    <td align="center">
      <strong>iOS</strong><br />
      <img src="docs/assets/ios.gif" width="280" />
    </td>
    <td align="center">
      <strong>Android</strong><br />
      <img src="docs/assets/android.gif" width="280" />
    </td>
  </tr>
</table>

## Example app

Run the example to see multiple targets, animated transitions, backdrop behavior, and tour navigation.

```sh
yarn example start
```

## Tips

- Render `<Spotlight />` once, near the root of the screen.
- No provider is required; keep spotlight state local to the screen or flow.
- Use `collapsable={false}` on custom target views if you wire refs manually.
- Keep tour steps stable with `useMemo`.
- Avoid triggering the same highlight repeatedly during an active animation; the hook already guards against duplicate same-target calls.

### React Navigation back behavior

If a tour is active and the user presses back, clear the tour before the screen is removed. With `@react-navigation/native-stack`, prefer `usePreventRemove` over a raw `beforeRemove` listener.

```tsx
import { useNavigation, usePreventRemove } from '@react-navigation/native';
import { Spotlight, useSpotlightTour } from 'react-native-nitro-spotlight';

function TourScreen() {
  const navigation = useNavigation();
  const tour = useSpotlightTour({ steps });

  usePreventRemove(tour.isActive, ({ data }) => {
    tour.stop();
    navigation.dispatch(data.action);
  });

  return <Spotlight controls={tour.spotlight} />;
}
```

For native-stack screens, also disable the Android/iOS back-button history menu for that route:

```tsx
<Stack.Screen
  name="Tour"
  component={TourScreen}
  options={{ headerBackButtonMenuEnabled: false }}
/>
```

This keeps the navigation workaround at the app/example level instead of coupling the library to React Navigation.

## Agent Skills

This repo includes Agent Skills so coding agents can learn this library faster and generate better code.

### Add the skill to your app project

From your app repo, install the user-facing skill:

```sh
npx skills add chanphiromsok/react-native-nitro-spotlight
```

Then ask your agent something like:

```txt
Use react-native-nitro-spotlight to add a 3-step onboarding tour to this screen.
```

The skill teaches agents:

- how to use `Spotlight`, `useSpotlight`, and `useSpotlightTour`
- when to use `react-native-teleport`
- how `allowOverlayClick` and `onBackdropPress` behave
- how to avoid duplicate animation hitches

Contributors working on this repo get the maintainer skill automatically — it lives in `.claude/skills/` and loads when you open the repo in Claude Code. No install step needed.

After the repo is public and installable, it can be discovered through the skills ecosystem / skills.sh.

## License

MIT
