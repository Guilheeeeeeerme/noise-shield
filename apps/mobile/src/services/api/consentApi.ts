import { api } from './client';
import { CONSENT_POLICY_VERSION } from '@noise-shield/shared';

export const consentApi = {
  get: () => api.getConsent(),
  update: (optIn: boolean) => api.putConsent(optIn, CONSENT_POLICY_VERSION),
};
