-- Create candidates table
CREATE TABLE public.candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    linkedin_url VARCHAR(500),
    primary_area VARCHAR(255),
    feedback_link VARCHAR(500)
);

CREATE INDEX idx_candidates_email ON public.candidates(email);

-- Add candidate_id and talent_acquisition_id to interviews
ALTER TABLE public.interviews ADD COLUMN candidate_id UUID REFERENCES public.candidates(id);
ALTER TABLE public.interviews ADD COLUMN talent_acquisition_id UUID REFERENCES public.profiles(id);

-- Drop the old JSONB column
ALTER TABLE public.interviews DROP COLUMN candidate_info;

CREATE INDEX idx_interviews_candidate_id ON public.interviews(candidate_id);
CREATE INDEX idx_interviews_talent_acquisition_id ON public.interviews(talent_acquisition_id);
