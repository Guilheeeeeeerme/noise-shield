import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { ProviderVerifier, VerifiedIdentity } from './provider.verifier';

@Injectable()
export class FacebookVerifier implements ProviderVerifier {
  constructor(private readonly config: ConfigService) {}

  async verify(idToken: string): Promise<VerifiedIdentity> {
    const appId = this.config.get('FACEBOOK_APP_ID');
    const appSecret = this.config.get('FACEBOOK_APP_SECRET');

    if (!appId || !appSecret) {
      if (process.env.NODE_ENV === 'development') {
        return { providerSubject: `dev-facebook-${idToken.slice(0, 8)}` };
      }
      throw new UnauthorizedException('Facebook not configured');
    }

    try {
      const url = `https://graph.facebook.com/debug_token?input_token=${idToken}&access_token=${appId}|${appSecret}`;
      const res = await fetch(url);
      const data = (await res.json()) as {
        data?: { is_valid?: boolean; user_id?: string };
      };
      if (!data.data?.is_valid || !data.data.user_id) {
        throw new UnauthorizedException('Invalid Facebook token');
      }
      return { providerSubject: data.data.user_id };
    } catch {
      if (process.env.NODE_ENV === 'development') {
        return { providerSubject: `dev-facebook-${idToken.slice(0, 8)}` };
      }
      throw new UnauthorizedException('Facebook token verification failed');
    }
  }
}
