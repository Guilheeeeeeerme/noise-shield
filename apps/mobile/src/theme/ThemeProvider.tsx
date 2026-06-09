import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { useColorScheme } from 'react-native';
import { getString, setString, STORAGE_KEYS } from '@/services/storage/mmkv';

export type ThemeMode = 'system' | 'light' | 'dark';

export const themeTokens = {
  light: {
    background: '#f8fafc',
    surface: '#ffffff',
    text: '#0f172a',
    textMuted: '#64748b',
    primary: '#0ea5e9',
    border: '#e2e8f0',
  },
  dark: {
    background: '#0f172a',
    surface: '#1e293b',
    text: '#f8fafc',
    textMuted: '#94a3b8',
    primary: '#38bdf8',
    border: '#334155',
  },
};

interface ThemeContextValue {
  mode: ThemeMode;
  resolved: 'light' | 'dark';
  tokens: typeof themeTokens.dark;
  setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const systemScheme = useColorScheme();
  const [mode, setModeState] = useState<ThemeMode>(
    (getString(STORAGE_KEYS.THEME) as ThemeMode) ?? 'system',
  );

  const resolved: 'light' | 'dark' =
    mode === 'system' ? (systemScheme === 'light' ? 'light' : 'dark') : mode;

  const setMode = (next: ThemeMode) => {
    setModeState(next);
    setString(STORAGE_KEYS.THEME, next);
  };

  return (
    <ThemeContext.Provider
      value={{ mode, resolved, tokens: themeTokens[resolved], setMode }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
