import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { AuthProvider, User } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { GoogleVerifier } from './providers/google.verifier';
import { AppleVerifier } from './providers/apple.verifier';
import { FacebookVerifier } from './providers/facebook.verifier';

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
    private readonly google: GoogleVerifier,
    private readonly apple: AppleVerifier,
    private readonly facebook: FacebookVerifier,
  ) {}

  async exchange(provider: AuthProvider, idToken: string) {
    const identity = await this.verifyProvider(provider, idToken);
    const now = new Date();

    const user = await this.prisma.user.upsert({
      where: {
        provider_providerSubject: {
          provider,
          providerSubject: identity.providerSubject,
        },
      },
      update: {
        email: identity.email,
        displayName: identity.displayName,
        lastLoginAt: now,
      },
      create: {
        provider,
        providerSubject: identity.providerSubject,
        email: identity.email,
        displayName: identity.displayName,
        lastLoginAt: now,
      },
    });

    return this.issueTokens(user);
  }

  async refresh(refreshToken: string) {
    try {
      const payload = this.jwt.verify(refreshToken, {
        secret: this.config.get('JWT_SECRET', 'dev-secret'),
      }) as { sub: string; type: string };
      if (payload.type !== 'refresh') throw new UnauthorizedException();
      const user = await this.prisma.user.findUnique({ where: { id: payload.sub } });
      if (!user) throw new UnauthorizedException();
      return this.issueTokens(user);
    } catch {
      throw new UnauthorizedException('Invalid refresh token');
    }
  }

  private async verifyProvider(provider: AuthProvider, idToken: string) {
    switch (provider) {
      case 'google':
        return this.google.verify(idToken);
      case 'apple':
        return this.apple.verify(idToken);
      case 'facebook':
        return this.facebook.verify(idToken);
      default:
        throw new UnauthorizedException('Unknown provider');
    }
  }

  private issueTokens(user: User) {
    const accessToken = this.jwt.sign({ sub: user.id, email: user.email });
    const refreshToken = this.jwt.sign(
      { sub: user.id, type: 'refresh' },
      { expiresIn: this.config.get('JWT_REFRESH_EXPIRES_IN', '30d') },
    );
    return {
      access_token: accessToken,
      refresh_token: refreshToken,
      expires_in: 900,
      user: {
        id: user.id,
        provider: user.provider,
        email: user.email,
        display_name: user.displayName,
        created_at: user.createdAt.toISOString(),
      },
    };
  }
}
