-- CreateEnum
CREATE TYPE "AuthProvider" AS ENUM ('google', 'apple', 'facebook');

-- CreateTable
CREATE TABLE "users" (
    "id" UUID NOT NULL,
    "provider" "AuthProvider" NOT NULL,
    "provider_subject" TEXT NOT NULL,
    "email" TEXT,
    "display_name" TEXT,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ NOT NULL,
    "last_login_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "user_preferences" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "key" TEXT NOT NULL,
    "value" JSONB NOT NULL,
    "server_received_at" TIMESTAMPTZ NOT NULL,
    "client_updated_at" TIMESTAMPTZ,

    CONSTRAINT "user_preferences_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "favorite_profiles" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "sound_id" TEXT NOT NULL,
    "label" TEXT,
    "sort_order" INTEGER NOT NULL DEFAULT 0,
    "server_received_at" TIMESTAMPTZ NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "favorite_profiles_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "consent_records" (
    "user_id" UUID NOT NULL,
    "acoustic_features_opt_in" BOOLEAN NOT NULL DEFAULT false,
    "consented_at" TIMESTAMPTZ,
    "revoked_at" TIMESTAMPTZ,
    "policy_version" TEXT NOT NULL,
    "server_received_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "consent_records_pkey" PRIMARY KEY ("user_id")
);

-- CreateTable
CREATE TABLE "session_feedback" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "session_id" TEXT NOT NULL,
    "sound_id" TEXT NOT NULL,
    "suggested_profile" TEXT,
    "helpful" BOOLEAN NOT NULL,
    "submitted_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "context" JSONB,

    CONSTRAINT "session_feedback_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "acoustic_feature_submissions" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "session_id" TEXT NOT NULL,
    "feature_schema_version" TEXT NOT NULL,
    "features" JSONB NOT NULL,
    "broad_profile_label" TEXT,
    "captured_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "acoustic_feature_submissions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "remote_configurations" (
    "id" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "payload" JSONB NOT NULL,
    "published_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "remote_configurations_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "experiment_assignments" (
    "user_id" UUID NOT NULL,
    "experiment_key" TEXT NOT NULL,
    "variant" TEXT NOT NULL,
    "assigned_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "experiment_assignments_pkey" PRIMARY KEY ("user_id","experiment_key")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_provider_provider_subject_key" ON "users"("provider", "provider_subject");

-- CreateIndex
CREATE UNIQUE INDEX "user_preferences_user_id_key_key" ON "user_preferences"("user_id", "key");

-- CreateIndex
CREATE UNIQUE INDEX "favorite_profiles_user_id_sound_id_key" ON "favorite_profiles"("user_id", "sound_id");

-- CreateIndex
CREATE INDEX "session_feedback_user_id_submitted_at_idx" ON "session_feedback"("user_id", "submitted_at" DESC);

-- CreateIndex
CREATE INDEX "acoustic_feature_submissions_captured_at_idx" ON "acoustic_feature_submissions"("captured_at");

-- AddForeignKey
ALTER TABLE "user_preferences" ADD CONSTRAINT "user_preferences_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "favorite_profiles" ADD CONSTRAINT "favorite_profiles_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "consent_records" ADD CONSTRAINT "consent_records_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "session_feedback" ADD CONSTRAINT "session_feedback_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "acoustic_feature_submissions" ADD CONSTRAINT "acoustic_feature_submissions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "experiment_assignments" ADD CONSTRAINT "experiment_assignments_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
