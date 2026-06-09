import { Module } from '@nestjs/common';
import { FeaturesController } from './features.controller';
import { FeaturesService } from './features.service';
import { ConsentModule } from '../consent/consent.module';

@Module({
  imports: [ConsentModule],
  controllers: [FeaturesController],
  providers: [FeaturesService],
})
export class FeaturesModule {}
