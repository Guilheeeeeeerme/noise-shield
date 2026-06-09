import {
  Body,
  Controller,
  Post,
  UseGuards,
  HttpCode,
  HttpStatus,
  ForbiddenException,
} from '@nestjs/common';
import { User } from '@prisma/client';
import { AcousticFeatureSubmissionSchema } from '@noise-shield/shared';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators/current-user.decorator';
import { ConsentService } from '../consent/consent.service';
import { FeaturesService } from './features.service';

@Controller('features')
@UseGuards(JwtAuthGuard)
export class FeaturesController {
  constructor(
    private readonly features: FeaturesService,
    private readonly consent: ConsentService,
  ) {}

  @Post('acoustic')
  @HttpCode(HttpStatus.CREATED)
  async submit(@CurrentUser() user: User, @Body() body: unknown) {
    const optedIn = await this.consent.isOptedIn(user.id);
    if (!optedIn) {
      throw new ForbiddenException('User has not opted in to feature collection');
    }
    const parsed = AcousticFeatureSubmissionSchema.parse(body);
    return this.features.submit(user.id, parsed);
  }
}
