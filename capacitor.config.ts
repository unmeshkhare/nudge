import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.graviton.nudge',
  appName: 'Nudge',
  webDir: 'www',
  backgroundColor: '#080b14',
  plugins: {
    LocalNotifications: {
      smallIcon: 'ic_stat_nudge',
      iconColor: '#6FF3FF'
    }
  }
};

export default config;
