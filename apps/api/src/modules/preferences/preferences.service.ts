import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class PreferencesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(userId: string) {
    const items = await this.prisma.userPreference.findMany({ where: { userId } });
    return {
      items: items.map((p) => ({
        key: p.key,
        value: p.value,
        server_received_at: p.serverReceivedAt.toISOString(),
      })),
    };
  }

  async upsert(
    userId: string,
    entries: Array<{ key: string; value: unknown; client_updated_at?: string }>,
  ) {
    const now = new Date();
    await this.prisma.$transaction(
      entries.map((entry) =>
        this.prisma.userPreference.upsert({
          where: { userId_key: { userId, key: entry.key } },
          update: {
            value: entry.value as Prisma.InputJsonValue,
            serverReceivedAt: now,
            clientUpdatedAt: entry.client_updated_at
              ? new Date(entry.client_updated_at)
              : undefined,
          },
          create: {
            userId,
            key: entry.key,
            value: entry.value as Prisma.InputJsonValue,
            serverReceivedAt: now,
            clientUpdatedAt: entry.client_updated_at
              ? new Date(entry.client_updated_at)
              : undefined,
          },
        }),
      ),
    );
    return this.list(userId);
  }
}
