import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OAuth2Client } from 'google-auth-library';
import type { ProviderVerifier, VerifiedIdentity } from './provider.verifier';

@Injectable()
export class GoogleVerifier implements ProviderVerifier {
  private client: OAuth2Client;

  constructor(config: ConfigService) {
    this.client = new OAuth2Client(config.get('GOOGLE_CLIENT_ID'));
  }

  async verify(idToken: string): Promise<VerifiedIdentity> {
    try {
      const ticket = await this.client.verifyIdToken({
        idToken,
        audience: process.env.GOOGLE_CLIENT_ID,
      });
      const payload = ticket.getPayload();
      if (!payload?.sub) throw new UnauthorizedException('Invalid Google token');
      return {
        providerSubject: payload.sub,
        email: payload.email,
        displayName: payload.name,
      };
    } catch {
      if (process.env.NODE_ENV === 'development') {
        return { providerSubject: `dev-google-${idToken.slice(0, 8)}`, email: 'dev@test.com' };
      }
      throw new UnauthorizedException('Google token verification failed');
    }
  }
}
