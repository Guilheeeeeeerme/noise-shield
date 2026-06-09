export interface VerifiedIdentity {
  providerSubject: string;
  email?: string;
  displayName?: string;
}

export interface ProviderVerifier {
  verify(idToken: string): Promise<VerifiedIdentity>;
}
