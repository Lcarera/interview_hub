import { Candidate } from './candidate.model';
import { Profile } from './profile.model';
import { ShadowingRequest } from './shadowing-request.model';

export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';

export interface Interview {
  id: string;
  interviewer: Profile;
  candidate: Candidate;
  talentAcquisition?: Profile;
  techStack: string;
  startTime: string;
  endTime: string;
  status: InterviewStatus;
  shadowingRequests?: ShadowingRequest[];
}
