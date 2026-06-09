import { create } from 'zustand';
import { api } from '@/services/api/client';
import { getString, remove, setString, STORAGE_KEYS } from '@/services/storage/mmkv';

interface User {
  id: string;
  provider: string;
  email?: string | null;
  display_name?: string | null;
}

interface AuthState {
  isAuthenticated: boolean;
  user: User | null;
  isLoading: boolean;
  hydrate: () => void;
  signIn: (provider: 'google' | 'apple' | 'facebook', idToken: string) => Promise<void>;
  signOut: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  user: null,
  isLoading: false,

  hydrate: () => {
    const token = getString(STORAGE_KEYS.ACCESS_TOKEN);
    const userJson = getString(STORAGE_KEYS.USER);
    if (token && userJson) {
      set({ isAuthenticated: true, user: JSON.parse(userJson) as User });
    }
  },

  signIn: async (provider, idToken) => {
    set({ isLoading: true });
    try {
      const result = await api.exchange(provider, idToken);
      set({ isAuthenticated: true, user: result.user, isLoading: false });
    } catch (e) {
      set({ isLoading: false });
      throw e;
    }
  },

  signOut: () => {
    remove(STORAGE_KEYS.ACCESS_TOKEN);
    remove(STORAGE_KEYS.REFRESH_TOKEN);
    remove(STORAGE_KEYS.USER);
    set({ isAuthenticated: false, user: null });
  },
}));
