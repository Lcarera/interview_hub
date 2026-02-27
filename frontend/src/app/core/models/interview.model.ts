import { Profile } from './profile.model';
import { ShadowingRequest } from './shadowing-request.model';

export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';

export interface Interview {
  id: string;
  interviewer: Profile;
  candidateInfo: Record<string, unknown>;
  techStack: string;
  startTime: string;
  endTime: string;
  status: InterviewStatus;
  googleEventId?: string;
  shadowingRequests?: ShadowingRequest[];
}
