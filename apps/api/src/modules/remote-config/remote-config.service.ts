import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class RemoteConfigService {
  constructor(private readonly prisma: PrismaService) {}

  async get(configId = 'production') {
    const config = await this.prisma.remoteConfiguration.findUnique({
      where: { id: configId },
    });
    if (!config) throw new NotFoundException('Remote config not found');
    return {
      version: config.version,
      payload: config.payload,
      published_at: config.publishedAt.toISOString(),
    };
  }
}
