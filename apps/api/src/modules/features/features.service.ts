import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class FeaturesService {
  constructor(private readonly prisma: PrismaService) {}

  async submit(
    userId: string,
    input: {
      session_id: string;
      feature_schema_version: string;
      broad_profile_label?: string | null;
      features: Record<string, unknown>;
      captured_at: string;
    },
  ) {
    const record = await this.prisma.acousticFeatureSubmission.create({
      data: {
        userId,
        sessionId: input.session_id,
        featureSchemaVersion: input.feature_schema_version,
        broadProfileLabel: input.broad_profile_label,
        features: input.features as Prisma.InputJsonValue,
        capturedAt: new Date(input.captured_at),
      },
    });
    return { id: record.id };
  }
}
