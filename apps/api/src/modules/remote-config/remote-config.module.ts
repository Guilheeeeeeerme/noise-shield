import { Module } from '@nestjs/common';
import { RemoteConfigController } from './remote-config.controller';
import { RemoteConfigService } from './remote-config.service';
import { ExperimentsService } from './experiments.service';

@Module({
  controllers: [RemoteConfigController],
  providers: [RemoteConfigService, ExperimentsService],
  exports: [RemoteConfigService, ExperimentsService],
})
export class RemoteConfigModule {}
