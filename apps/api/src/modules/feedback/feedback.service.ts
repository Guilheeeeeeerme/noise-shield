import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class FeedbackService {
  constructor(private readonly prisma: PrismaService) {}

  async create(
    userId: string,
    input: {
      session_id: string;
      sound_id: string;
      suggested_profile?: string | null;
      helpful: boolean;
      context?: Record<string, unknown>;
    },
  ) {
    const record = await this.prisma.sessionFeedback.create({
      data: {
        userId,
        sessionId: input.session_id,
        soundId: input.sound_id,
        suggestedProfile: input.suggested_profile,
        helpful: input.helpful,
        context: input.context as Prisma.InputJsonValue,
      },
    });
    return {
      id: record.id,
      session_id: record.sessionId,
      sound_id: record.soundId,
      suggested_profile: record.suggestedProfile,
      helpful: record.helpful,
      context: record.context,
      submitted_at: record.submittedAt.toISOString(),
    };
  }
}
