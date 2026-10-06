// Primary API — this is all most consumers need
export { Spotlight } from './Spotlight';
export type {
  SpotlightComponentProps,
  SpotlightShape,
  SpotlightAnimation,
} from './Spotlight';
export { useSpotlight } from './useSpotlight';
export type { SpotlightControls, HighlightOptions } from './useSpotlight';
export { useSpotlightTargets } from './useSpotlightTargets';
export type {
  SpotlightTargets,
  SpotlightTargetProps,
} from './useSpotlightTargets';
export { useSpotlightTour } from './useSpotlightTour';
export type {
  SpotlightTourControls,
  SpotlightTourOptions,
  SpotlightTourStep,
} from './useSpotlightTour';

// Escape hatch — for advanced use (custom hybridRef wiring, direct method calls)
export { SpotlightView } from './SpotlightView';
export type {
  SpotlightView as SpotlightViewType,
  Rect,
} from './Spotlight.nitro';
