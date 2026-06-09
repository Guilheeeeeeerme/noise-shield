import { Body, Controller, Post } from '@nestjs/common';
import { AuthExchangeRequestSchema } from '@noise-shield/shared';
import { AuthService } from './auth.service';

@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Post('exchange')
  async exchange(@Body() body: unknown) {
    const parsed = AuthExchangeRequestSchema.parse(body);
    return this.auth.exchange(parsed.provider, parsed.id_token);
  }

  @Post('refresh')
  async refresh(@Body() body: { refresh_token: string }) {
    return this.auth.refresh(body.refresh_token);
  }
}
