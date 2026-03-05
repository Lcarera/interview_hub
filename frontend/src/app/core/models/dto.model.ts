import { InterviewStatus } from './interview.model';

export interface CreateInterviewRequest {
  interviewerId: string;
  candidateId: string;
  talentAcquisitionId?: string;
  techStack: string;
  startTime: string;
  endTime: string;
}

export interface UpdateInterviewRequest {
  candidateId: string;
  talentAcquisitionId?: string;
  techStack: string;
  startTime: string;
  endTime: string;
  status: InterviewStatus;
}

export interface RejectShadowingRequest {
  reason: string;
}
