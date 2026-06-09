import { z } from 'zod';

export const AuthProviderSchema = z.enum(['google', 'apple', 'facebook']);
export type AuthProvider = z.infer<typeof AuthProviderSchema>;

export const AuthExchangeRequestSchema = z.object({
  provider: AuthProviderSchema,
  id_token: z.string().min(1),
  device_info: z.record(z.unknown()).optional(),
});

export const UserSchema = z.object({
  id: z.string().uuid(),
  provider: AuthProviderSchema,
  email: z.string().email().nullable().optional(),
  display_name: z.string().nullable().optional(),
  created_at: z.string().datetime(),
});

export const AuthExchangeResponseSchema = z.object({
  access_token: z.string(),
  refresh_token: z.string(),
  expires_in: z.number().int(),
  user: UserSchema,
});

export const PreferenceEntrySchema = z.object({
  key: z.string(),
  value: z.unknown(),
  server_received_at: z.string().datetime(),
});

export const PreferenceListResponseSchema = z.object({
  items: z.array(PreferenceEntrySchema),
});

export const PreferenceUpsertRequestSchema = z.object({
  items: z.array(
    z.object({
      key: z.string(),
      value: z.unknown(),
      client_updated_at: z.string().datetime().optional(),
    }),
  ),
});

export const FavoriteProfileSchema = z.object({
  sound_id: z.string(),
  label: z.string().nullable().optional(),
  sort_order: z.number().int().optional(),
  server_received_at: z.string().datetime(),
});

export const FavoriteProfileInputSchema = z.object({
  sound_id: z.string(),
  label: z.string().optional(),
  sort_order: z.number().int().optional(),
});

export const ConsentRecordSchema = z.object({
  acoustic_features_opt_in: z.boolean(),
  policy_version: z.string(),
  consented_at: z.string().datetime().nullable().optional(),
  revoked_at: z.string().datetime().nullable().optional(),
  server_received_at: z.string().datetime(),
});

export const ConsentUpdateSchema = z.object({
  acoustic_features_opt_in: z.boolean(),
  policy_version: z.string(),
});

export const SessionFeedbackInputSchema = z.object({
  session_id: z.string().uuid(),
  sound_id: z.string(),
  suggested_profile: z.string().nullable().optional(),
  helpful: z.boolean(),
  context: z.record(z.unknown()).optional(),
});

export const SessionFeedbackSchema = SessionFeedbackInputSchema.extend({
  id: z.string().uuid(),
  submitted_at: z.string().datetime(),
});

export const AcousticFeatureSubmissionSchema = z.object({
  session_id: z.string().uuid(),
  feature_schema_version: z.string(),
  broad_profile_label: z.string().nullable().optional(),
  features: z.object({
    rms_db: z.number().optional(),
    level_bucket: z.enum(['low', 'medium', 'high']).optional(),
    band_energy_ratios: z.array(z.number()).optional(),
  }),
  captured_at: z.string().datetime(),
});

export const RemoteConfigurationSchema = z.object({
  version: z.number().int(),
  payload: z.object({
    classifier_threshold: z.number().optional(),
    auto_apply_debounce_ms: z.number().int().optional(),
    crossfade_ms: z.number().int().optional(),
    model_version: z.string().optional(),
    experiments: z.record(z.unknown()).optional(),
  }),
  published_at: z.string().datetime(),
});

export const ErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
});

export const ThemePreferenceSchema = z.enum(['system', 'light', 'dark']);
export const LanguagePreferenceSchema = z.enum(['en', 'pt']);
