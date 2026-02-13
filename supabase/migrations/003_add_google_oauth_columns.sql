-- Add Google OAuth columns to profiles for direct Google authentication
ALTER TABLE public.profiles ADD COLUMN google_sub VARCHAR(255) UNIQUE;
ALTER TABLE public.profiles ADD COLUMN google_access_token TEXT;
ALTER TABLE public.profiles ADD COLUMN google_refresh_token TEXT;
ALTER TABLE public.profiles ADD COLUMN google_token_expiry TIMESTAMPTZ;

CREATE INDEX idx_profiles_google_sub ON public.profiles(google_sub);
