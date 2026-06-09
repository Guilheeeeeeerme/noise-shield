import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

const EXPERIMENTS = ['crossfade_duration', 'classifier_threshold'] as const;

@Injectable()
export class ExperimentsService {
  constructor(private readonly prisma: PrismaService) {}

  async getOrAssign(userId: string, experimentKey: string): Promise<string> {
    const existing = await this.prisma.experimentAssignment.findUnique({
      where: { userId_experimentKey: { userId, experimentKey } },
    });
    if (existing) return existing.variant;

    const variant = Math.random() < 0.5 ? 'control' : 'treatment';
    await this.prisma.experimentAssignment.create({
      data: { userId, experimentKey, variant },
    });
    return variant;
  }

  async getAllForUser(userId: string): Promise<Record<string, string>> {
    const result: Record<string, string> = {};
    for (const key of EXPERIMENTS) {
      result[key] = await this.getOrAssign(userId, key);
    }
    return result;
  }
}
