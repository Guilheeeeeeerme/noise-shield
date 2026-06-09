import { Controller, Get, UseGuards } from '@nestjs/common';
import { User } from '@prisma/client';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { CurrentUser } from '../../common/decorators/current-user.decorator';
import { RemoteConfigService } from './remote-config.service';
import { ExperimentsService } from './experiments.service';

@Controller('remote-config')
@UseGuards(JwtAuthGuard)
export class RemoteConfigController {
  constructor(
    private readonly remoteConfig: RemoteConfigService,
    private readonly experiments: ExperimentsService,
  ) {}

  @Get()
  async get(@CurrentUser() user: User) {
    const config = await this.remoteConfig.get();
    const experimentVariants = await this.experiments.getAllForUser(user.id);
    return {
      ...config,
      payload: {
        ...(config.payload as Record<string, unknown>),
        experiments: experimentVariants,
      },
    };
  }
}
