import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class ConsentService {
  constructor(private readonly prisma: PrismaService) {}

  async get(userId: string) {
    const record = await this.prisma.consentRecord.findUnique({ where: { userId } });
    if (!record) {
      return this.defaultRecord();
    }
    return this.toDto(record);
  }

  async update(
    userId: string,
    acousticFeaturesOptIn: boolean,
    policyVersion: string,
  ) {
    const now = new Date();
    const record = await this.prisma.consentRecord.upsert({
      where: { userId },
      update: {
        acousticFeaturesOptIn,
        policyVersion,
        serverReceivedAt: now,
        consentedAt: acousticFeaturesOptIn ? now : undefined,
        revokedAt: acousticFeaturesOptIn ? null : now,
      },
      create: {
        userId,
        acousticFeaturesOptIn,
        policyVersion,
        serverReceivedAt: now,
        consentedAt: acousticFeaturesOptIn ? now : null,
        revokedAt: acousticFeaturesOptIn ? null : now,
      },
    });
    return this.toDto(record);
  }

  async isOptedIn(userId: string): Promise<boolean> {
    const record = await this.prisma.consentRecord.findUnique({ where: { userId } });
    return record?.acousticFeaturesOptIn ?? false;
  }

  private defaultRecord() {
    return {
      acoustic_features_opt_in: false,
      policy_version: '2026-06-09',
      consented_at: null,
      revoked_at: null,
      server_received_at: new Date().toISOString(),
    };
  }

  private toDto(record: {
    acousticFeaturesOptIn: boolean;
    policyVersion: string;
    consentedAt: Date | null;
    revokedAt: Date | null;
    serverReceivedAt: Date;
  }) {
    return {
      acoustic_features_opt_in: record.acousticFeaturesOptIn,
      policy_version: record.policyVersion,
      consented_at: record.consentedAt?.toISOString() ?? null,
      revoked_at: record.revokedAt?.toISOString() ?? null,
      server_received_at: record.serverReceivedAt.toISOString(),
    };
  }
}
