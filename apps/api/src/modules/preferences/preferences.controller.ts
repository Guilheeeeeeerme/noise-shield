import { Body, Controller, Get, Put, UseGuards } from '@nestjs/common';
import { User } from '@prisma/client';
import { PreferenceUpsertRequestSchema } from '@noise-shield/shared';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators/current-user.decorator';
import { PreferencesService } from './preferences.service';

@Controller('preferences')
@UseGuards(JwtAuthGuard)
export class PreferencesController {
  constructor(private readonly preferences: PreferencesService) {}

  @Get()
  list(@CurrentUser() user: User) {
    return this.preferences.list(user.id);
  }

  @Put()
  upsert(@CurrentUser() user: User, @Body() body: unknown) {
    const parsed = PreferenceUpsertRequestSchema.parse(body);
    return this.preferences.upsert(
      user.id,
      parsed.items.map((item) => ({ ...item, value: item.value ?? null })),
    );
  }
}
