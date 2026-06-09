import {
  DEFAULT_ANALYSIS_CONFIG,
  type AudioAnalysisConfig,
  type AudioAnalysisPort,
  type NoiseEstimate,
} from './ports';
import { bucketizeLevel, computeRms, rmsToDb } from './analysis/levelEstimator';
import { classifyProfile, extractBandRatios } from './analysis/profileClassifier';
import {
  MicCaptureAdapter,
  SyntheticMicCaptureAdapter,
} from './capture/MicCaptureAdapter';

export class HeuristicAnalysisPort implements AudioAnalysisPort {
  private config: AudioAnalysisConfig = DEFAULT_ANALYSIS_CONFIG;
  private capture: MicCaptureAdapter;
  private listeners = new Set<(estimate: NoiseEstimate) => void>();
  private latest: NoiseEstimate | null = null;
  private refreshTimer: ReturnType<typeof setInterval> | null = null;
  private lastSamples: Float32Array | null = null;

  constructor(capture?: MicCaptureAdapter) {
    this.capture = capture ?? new SyntheticMicCaptureAdapter();
  }

  async start(config?: Partial<AudioAnalysisConfig>): Promise<void> {
    this.config = { ...DEFAULT_ANALYSIS_CONFIG, ...config };
    await this.capture.start((samples) => {
      this.lastSamples = samples;
    });
    this.refreshTimer = setInterval(() => this.emitEstimate(), this.config.refreshIntervalMs);
  }

  async stop(): Promise<void> {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
    await this.capture.stop();
    this.lastSamples = null;
    this.latest = null;
  }

  getLatestEstimate(): NoiseEstimate | null {
    return this.latest;
  }

  onEstimate(callback: (estimate: NoiseEstimate) => void): () => void {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  private emitEstimate(): void {
    if (!this.lastSamples) return;

    const rms = computeRms(this.lastSamples);
    const rmsDb = rmsToDb(rms);
    const levelBucket = bucketizeLevel(rmsDb);
    const bands = extractBandRatios(this.lastSamples);
    const { broadProfile, confidence } = classifyProfile({
      ...bands,
      rmsDb,
      levelBucket,
    });

    if (confidence < this.config.minConfidence) return;

    this.latest = {
      levelBucket,
      rmsDb,
      broadProfile,
      confidence,
      capturedAt: new Date().toISOString(),
    };

    this.listeners.forEach((cb) => cb(this.latest!));
  }
}
