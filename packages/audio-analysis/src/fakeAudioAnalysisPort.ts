import type { AudioAnalysisPort, NoiseEstimate } from './ports';

export class FakeAudioAnalysisPort implements AudioAnalysisPort {
  private estimates: NoiseEstimate[] = [];
  private index = 0;
  private listeners = new Set<(estimate: NoiseEstimate) => void>();
  private interval: ReturnType<typeof setInterval> | null = null;
  private latest: NoiseEstimate | null = null;

  constructor(estimates?: NoiseEstimate[]) {
    this.estimates = estimates ?? [
      {
        levelBucket: 'medium',
        rmsDb: -30,
        broadProfile: 'cafe',
        confidence: 0.7,
        capturedAt: new Date().toISOString(),
      },
    ];
  }

  async start(): Promise<void> {
    this.index = 0;
    this.interval = setInterval(() => {
      const estimate = this.estimates[this.index % this.estimates.length];
      this.latest = { ...estimate, capturedAt: new Date().toISOString() };
      this.index++;
      this.listeners.forEach((cb) => cb(this.latest!));
    }, 500);
  }

  async stop(): Promise<void> {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
    this.latest = null;
  }

  getLatestEstimate(): NoiseEstimate | null {
    return this.latest;
  }

  onEstimate(callback: (estimate: NoiseEstimate) => void): () => void {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }
}
