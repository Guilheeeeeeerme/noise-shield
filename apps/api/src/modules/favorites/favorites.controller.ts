import { Body, Controller, Get, Put, UseGuards } from '@nestjs/common';
import { User } from '@prisma/client';
import { z } from 'zod';
import { FavoriteProfileInputSchema } from '@noise-shield/shared';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators/current-user.decorator';
import { FavoritesService } from './favorites.service';

const FavoritesUpsertSchema = z.object({
  items: z.array(FavoriteProfileInputSchema),
});

@Controller('favorites')
@UseGuards(JwtAuthGuard)
export class FavoritesController {
  constructor(private readonly favorites: FavoritesService) {}

  @Get()
  list(@CurrentUser() user: User) {
    return this.favorites.list(user.id);
  }

  @Put()
  upsert(@CurrentUser() user: User, @Body() body: unknown) {
    const parsed = FavoritesUpsertSchema.parse(body);
    return this.favorites.upsert(user.id, parsed.items);
  }
}
