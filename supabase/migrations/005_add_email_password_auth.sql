-- Add password and email verification columns to profiles
ALTER TABLE public.profiles ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE public.profiles ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark existing Google OAuth profiles as verified
UPDATE public.profiles SET email_verified = TRUE WHERE google_sub IS NOT NULL;

-- Create verification tokens table (used for email verification and password reset)
CREATE TABLE public.verification_tokens (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    token_type VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_tokens_token ON public.verification_tokens(token);
CREATE INDEX idx_verification_tokens_profile_id ON public.verification_tokens(profile_id);
