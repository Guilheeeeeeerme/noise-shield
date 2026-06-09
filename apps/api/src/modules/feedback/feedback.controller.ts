import { Body, Controller, Post, UseGuards, HttpCode, HttpStatus } from '@nestjs/common';
import { User } from '@prisma/client';
import { SessionFeedbackInputSchema } from '@noise-shield/shared';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators/current-user.decorator';
import { FeedbackService } from './feedback.service';

@Controller('feedback')
@UseGuards(JwtAuthGuard)
export class FeedbackController {
  constructor(private readonly feedback: FeedbackService) {}

  @Post('session')
  @HttpCode(HttpStatus.CREATED)
  submit(@CurrentUser() user: User, @Body() body: unknown) {
    const parsed = SessionFeedbackInputSchema.parse(body);
    return this.feedback.create(user.id, parsed);
  }
}
