-- Add reason column to shadowing_requests for rejection reasons
alter table public.shadowing_requests add column reason text;
