/** Bundled analysis tuning — FR-034. Remote config overrides when signed in + online. */
export const BUNDLED_ANALYSIS_DEFAULTS = {
  classifier_threshold: 0.55,
  auto_apply_debounce_ms: 5000,
  crossfade_ms: 1200,
  model_version: 'heuristic-1.0',
} as const;

export type AnalysisTuningConfig = {
  classifier_threshold: number;
  auto_apply_debounce_ms: number;
  crossfade_ms: number;
  model_version: string;
};
