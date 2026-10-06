export type RootStackParamList = {
  Home: undefined;
  Manual: undefined;
  Tour: undefined;
  CustomState: undefined;
  Touch: undefined;
  Lifecycle: undefined;
  // `arrive` is a changing token: the spotlight morphs onto this screen's
  // target after the transition (see spotlight/useSpotlightOnArrive.ts).
  Teleport: { arrive?: number } | undefined;
  TeleportDetails: { arrive?: number } | undefined;
  FullWindow: undefined;
  Shape: undefined;
  Sheet: undefined;
};
