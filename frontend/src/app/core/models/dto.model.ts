import { InterviewStatus } from './interview.model';

export interface CreateInterviewRequest {
  interviewerId: string;
  candidateInfo?: Record<string, unknown>;
  techStack: string;
  startTime: string;
  endTime: string;
}

export interface UpdateInterviewRequest {
  candidateInfo?: Record<string, unknown>;
  techStack: string;
  startTime: string;
  endTime: string;
  status: InterviewStatus;
}

export interface RejectShadowingRequest {
  reason: string;
}
