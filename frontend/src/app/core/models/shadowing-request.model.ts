import { Profile } from './profile.model';
import { Interview } from './interview.model';

export type ShadowingRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface ShadowingRequest {
  id: string;
  interview?: Interview;
  shadower: Profile;
  status: ShadowingRequestStatus;
  reason?: string;
}
