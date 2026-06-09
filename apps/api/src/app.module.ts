import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { PrismaModule } from './prisma/prisma.module';
import { HealthModule } from './modules/health/health.module';
import { AuthModule } from './modules/auth/auth.module';
import { UsersModule } from './modules/users/users.module';
import { PreferencesModule } from './modules/preferences/preferences.module';
import { FavoritesModule } from './modules/favorites/favorites.module';
import { ConsentModule } from './modules/consent/consent.module';
import { FeedbackModule } from './modules/feedback/feedback.module';
import { FeaturesModule } from './modules/features/features.module';
import { RemoteConfigModule } from './modules/remote-config/remote-config.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    PrismaModule,
    HealthModule,
    AuthModule,
    UsersModule,
    PreferencesModule,
    FavoritesModule,
    ConsentModule,
    FeedbackModule,
    FeaturesModule,
    RemoteConfigModule,
  ],
})
export class AppModule {}
