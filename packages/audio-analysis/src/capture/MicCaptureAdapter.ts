export type SampleCallback = (samples: Float32Array) => void;

export interface MicCaptureAdapter {
  start(onSamples: SampleCallback): Promise<void>;
  stop(): Promise<void>;
  isCapturing(): boolean;
}

/**
 * MVP stub — real implementation uses expo-av or native PCM bridge on mobile.
 * Generates synthetic ambient-like samples for development and tests.
 */
export class SyntheticMicCaptureAdapter implements MicCaptureAdapter {
  private interval: ReturnType<typeof setInterval> | null = null;
  private capturing = false;
  private noiseLevel = 0.15;

  async start(onSamples: SampleCallback): Promise<void> {
    if (this.capturing) return;
    this.capturing = true;
    this.interval = setInterval(() => {
      this.noiseLevel += (Math.random() - 0.5) * 0.05;
      this.noiseLevel = Math.max(0.02, Math.min(0.5, this.noiseLevel));
      const samples = new Float32Array(1024);
      for (let i = 0; i < samples.length; i++) {
        samples[i] = (Math.random() - 0.5) * 2 * this.noiseLevel;
      }
      onSamples(samples);
    }, 200);
  }

  async stop(): Promise<void> {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
    this.capturing = false;
  }

  isCapturing(): boolean {
    return this.capturing;
  }
}
