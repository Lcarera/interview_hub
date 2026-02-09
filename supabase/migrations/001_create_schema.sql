-- Interview Hub schema migration
-- Run this in the Supabase SQL Editor after creating your project

-- profiles: synced with Supabase auth.users
create table public.profiles (
    id uuid primary key,
    email varchar(255) not null,
    role varchar(255),
    calendar_email varchar(255)
);

-- interviews
create table public.interviews (
    id uuid primary key default gen_random_uuid(),
    google_event_id varchar(255),
    interviewer_id uuid not null references public.profiles(id),
    candidate_info jsonb,
    tech_stack varchar(255),
    start_time timestamptz not null,
    end_time timestamptz not null,
    status varchar(50) not null
);

-- shadowing_requests
create table public.shadowing_requests (
    id uuid primary key default gen_random_uuid(),
    interview_id uuid not null references public.interviews(id),
    shadower_id uuid not null references public.profiles(id),
    status varchar(50) not null
);

-- indexes for common queries
create index idx_interviews_interviewer_id on public.interviews(interviewer_id);
create index idx_interviews_status on public.interviews(status);
create index idx_shadowing_requests_interview_id on public.shadowing_requests(interview_id);
create index idx_shadowing_requests_shadower_id on public.shadowing_requests(shadower_id);
