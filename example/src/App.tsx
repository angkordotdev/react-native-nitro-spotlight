import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { StatusBar } from 'expo-status-bar';
import { StyleSheet } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PortalHost, PortalProvider } from 'react-native-teleport';
import type { RootStackParamList } from './navigation/types';
import {
  ROOT_SPOTLIGHT_HOST,
  RootSpotlightProvider,
} from './spotlight/RootSpotlight';
import { CustomStateScreen } from './screens/CustomStateScreen';
import { FullWindowScreen } from './screens/FullWindowScreen';
import { HomeScreen } from './screens/HomeScreen';
import { LifecycleScreen } from './screens/LifecycleScreen';
import { ManualScreen } from './screens/ManualScreen';
import { TeleportDetailsScreen } from './screens/TeleportDetailsScreen';
import { TeleportScreen } from './screens/TeleportScreen';
import { TouchScreen } from './screens/TouchScreen';
import { ShapeScreen } from './screens/ShapeScreen';
import { SheetScreen } from './screens/SheetScreen';
import { TourScreen } from './screens/TourScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <SafeAreaProvider>
      <PortalProvider>
        <RootSpotlightProvider>
          <NavigationContainer>
            <StatusBar style="light" />
            <Stack.Navigator
              screenOptions={{
                headerStyle: { backgroundColor: '#101624' },
                headerTintColor: '#F5F8FF',
                headerTitleStyle: { fontWeight: '800' },
                contentStyle: { backgroundColor: '#101624' },
              }}
            >
              <Stack.Screen
                name="Home"
                component={HomeScreen}
                options={{ headerShown: false }}
              />
              <Stack.Screen name="Manual" component={ManualScreen} />
              <Stack.Screen
                name="Tour"
                component={TourScreen}
                options={{ headerBackButtonMenuEnabled: false }}
              />
              <Stack.Screen
                name="CustomState"
                component={CustomStateScreen}
                options={{
                  title: 'Custom state',
                  headerBackButtonMenuEnabled: false,
                }}
              />
              <Stack.Screen name="Touch" component={TouchScreen} />
              <Stack.Screen name="Lifecycle" component={LifecycleScreen} />
              <Stack.Screen
                name="Teleport"
                component={TeleportScreen}
                options={{ title: 'Teleport' }}
              />
              <Stack.Screen
                name="TeleportDetails"
                component={TeleportDetailsScreen}
                options={{ title: 'Another screen' }}
              />
              <Stack.Screen name="FullWindow" component={FullWindowScreen} />
              <Stack.Screen
                name="Shape"
                component={ShapeScreen}
                options={{ title: 'Circle cutout' }}
              />
              <Stack.Screen
                name="Sheet"
                component={SheetScreen}
                options={{
                  title: 'Sheet spotlight',
                  presentation: 'formSheet',
                  sheetGrabberVisible: true,
                  sheetAllowedDetents: [0.5, 0.9],
                }}
              />
            </Stack.Navigator>
          </NavigationContainer>
        </RootSpotlightProvider>
        {/* Sibling AFTER NavigationContainer: renders above the native header. */}
        <PortalHost name={ROOT_SPOTLIGHT_HOST} style={styles.host} />
      </PortalProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  host: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 },
});
