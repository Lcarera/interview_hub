export interface Interview {
  id: string;
  interviewerId: string;
  candidateInfo: Record<string, unknown>;
  startTime: string;
  endTime: string;
  status: string;
  googleEventId?: string;
}
