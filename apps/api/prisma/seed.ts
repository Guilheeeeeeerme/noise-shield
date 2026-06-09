import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  await prisma.remoteConfiguration.upsert({
    where: { id: 'production' },
    update: {
      version: 1,
      payload: {
        classifier_threshold: 0.55,
        auto_apply_debounce_ms: 5000,
        crossfade_ms: 1200,
        model_version: 'heuristic-1.0',
        experiments: {},
      },
      publishedAt: new Date(),
    },
    create: {
      id: 'production',
      version: 1,
      payload: {
        classifier_threshold: 0.55,
        auto_apply_debounce_ms: 5000,
        crossfade_ms: 1200,
        model_version: 'heuristic-1.0',
        experiments: {},
      },
      publishedAt: new Date(),
    },
  });
}

main()
  .catch(console.error)
  .finally(() => prisma.$disconnect());
