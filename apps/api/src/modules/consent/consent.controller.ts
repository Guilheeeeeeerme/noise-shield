import { Body, Controller, Get, Put, UseGuards } from '@nestjs/common';
import { User } from '@prisma/client';
import { ConsentUpdateSchema } from '@noise-shield/shared';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators/current-user.decorator';
import { ConsentService } from './consent.service';

@Controller('consent')
@UseGuards(JwtAuthGuard)
export class ConsentController {
  constructor(private readonly consent: ConsentService) {}

  @Get()
  get(@CurrentUser() user: User) {
    return this.consent.get(user.id);
  }

  @Put()
  update(@CurrentUser() user: User, @Body() body: unknown) {
    const parsed = ConsentUpdateSchema.parse(body);
    return this.consent.update(
      user.id,
      parsed.acoustic_features_opt_in,
      parsed.policy_version,
    );
  }
}
