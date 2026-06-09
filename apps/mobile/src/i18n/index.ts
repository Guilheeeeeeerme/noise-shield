import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { getString, STORAGE_KEYS } from '@/services/storage/mmkv';
import en from './locales/en.json';
import pt from './locales/pt.json';

const savedLang = getString(STORAGE_KEYS.LANGUAGE) ?? 'en';

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, pt: { translation: pt } },
  lng: savedLang,
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

export default i18n;
