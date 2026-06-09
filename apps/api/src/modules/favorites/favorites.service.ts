import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class FavoritesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(userId: string) {
    const items = await this.prisma.favoriteProfile.findMany({
      where: { userId },
      orderBy: { sortOrder: 'asc' },
    });
    return {
      items: items.map((f) => ({
        sound_id: f.soundId,
        label: f.label,
        sort_order: f.sortOrder,
        server_received_at: f.serverReceivedAt.toISOString(),
      })),
    };
  }

  async upsert(
    userId: string,
    entries: Array<{ sound_id: string; label?: string; sort_order?: number }>,
  ) {
    const now = new Date();
    await this.prisma.$transaction(
      entries.map((entry, index) =>
        this.prisma.favoriteProfile.upsert({
          where: { userId_soundId: { userId, soundId: entry.sound_id } },
          update: {
            label: entry.label,
            sortOrder: entry.sort_order ?? index,
            serverReceivedAt: now,
          },
          create: {
            userId,
            soundId: entry.sound_id,
            label: entry.label,
            sortOrder: entry.sort_order ?? index,
            serverReceivedAt: now,
          },
        }),
      ),
    );
    return this.list(userId);
  }
}
