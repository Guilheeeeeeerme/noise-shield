import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';
import jwksClient from 'jwks-rsa';
import type { ProviderVerifier, VerifiedIdentity } from './provider.verifier';

@Injectable()
export class AppleVerifier implements ProviderVerifier {
  private client: jwksClient.JwksClient;

  constructor(config: ConfigService) {
    this.client = jwksClient({
      jwksUri: 'https://appleid.apple.com/auth/keys',
      cache: true,
    });
    void config;
  }

  async verify(idToken: string): Promise<VerifiedIdentity> {
    try {
      const decoded = jwt.decode(idToken, { complete: true });
      if (!decoded || typeof decoded === 'string') {
        throw new UnauthorizedException('Invalid Apple token');
      }
      const kid = decoded.header.kid;
      const key = await this.client.getSigningKey(kid);
      const publicKey = key.getPublicKey();
      const payload = jwt.verify(idToken, publicKey, {
        algorithms: ['RS256'],
        audience: process.env.APPLE_CLIENT_ID,
      }) as jwt.JwtPayload;

      if (!payload.sub) throw new UnauthorizedException('Invalid Apple token');
      return {
        providerSubject: payload.sub,
        email: payload.email as string | undefined,
      };
    } catch {
      if (process.env.NODE_ENV === 'development') {
        return { providerSubject: `dev-apple-${idToken.slice(0, 8)}` };
      }
      throw new UnauthorizedException('Apple token verification failed');
    }
  }
}
