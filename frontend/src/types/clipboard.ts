export interface ClipboardEntry {
  id: number;
  content: string;
  capturedAt: string;
  type: string;
  technology: string;
  category: string;
  // Phase 19 — multi-label classification fields (may be absent on older entries)
  language?: string;
  technologies?: string;  // comma-joined, e.g. "SPRING_BOOT,JAVA"
  categories?: string;    // comma-joined, e.g. "PROGRAMMING,WEB"
  sensitive?: boolean;
  confidence?: number;
}

export interface ClipboardExplanationResponse {
  id: number;
  explanation: string;
}

export interface ClipboardSummaryResponse {
  id: number;
  summary: string;
}

export interface ClipboardAskRequest {
  question: string;
}

export interface ClipboardAskResponse {
  answer: string;
  sources?: number[];
}

export interface SearchParams {
  q?: string;
  type?: string;
  technology?: string;
  category?: string;
}
