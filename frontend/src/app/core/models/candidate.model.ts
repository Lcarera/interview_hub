export interface Candidate {
  id: string;
  name: string;
  email: string;
  linkedinUrl?: string;
  primaryArea?: string;
  feedbackLink?: string;
}

export interface CandidateRequest {
  name: string;
  email: string;
  linkedinUrl?: string;
  primaryArea?: string;
  feedbackLink?: string;
}
