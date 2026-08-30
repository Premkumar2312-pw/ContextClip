export interface ClipboardEntry {
  id: number;
  content: string;
  capturedAt: string;
  type: string;
  technology: string;
  category: string;
}

export interface ClipboardExplanationResponse {
  id: number;
  explanation: string;
}

export interface ClipboardSummaryResponse {
  id: number;
  summary: string;
}

export interface SearchParams {
  q?: string;
  type?: string;
  technology?: string;
  category?: string;
}
