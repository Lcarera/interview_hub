-- Enable Row Level Security on all public tables
-- The app connects via JDBC as the postgres role (superuser), which bypasses RLS.
-- This migration blocks access through Supabase's PostgREST API (anon/authenticated roles)
-- without affecting the Spring Boot backend.

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.interviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.shadowing_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.candidates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verification_tokens ENABLE ROW LEVEL SECURITY;
