import React, { createContext, useContext, useState, ReactNode } from 'react';
import { ClipboardAskResponse, ClipboardEntry } from '../types/clipboard';

export type AskState = 'idle' | 'loading' | 'success' | 'error';

export interface AskClipboardContextType {
  question: string;
  setQuestion: (q: string) => void;
  validationError: string | null;
  setValidationError: (err: string | null) => void;
  state: AskState;
  setState: (s: AskState) => void;
  result: ClipboardAskResponse | null;
  setResult: (r: ClipboardAskResponse | null) => void;
  errorMessage: string | null;
  setErrorMessage: (msg: string | null) => void;
  copiedAnswer: boolean;
  setCopiedAnswer: (copied: boolean) => void;
  historyEntries: ClipboardEntry[];
  setHistoryEntries: React.Dispatch<React.SetStateAction<ClipboardEntry[]>>;
  entriesLoaded: boolean;
  setEntriesLoaded: React.Dispatch<React.SetStateAction<boolean>>;
  selectedEntryId: number | null;
  setSelectedEntryId: (id: number | null) => void;
  selectedEntry: ClipboardEntry | null;
  setSelectedEntry: (entry: ClipboardEntry | null) => void;
  clearAskState: () => void;
}

const AskClipboardContext = createContext<AskClipboardContextType | undefined>(undefined);

const ASK_STORAGE_KEY = 'contextclip_ask_session';

interface PersistedAskSession {
  question: string;
  result: ClipboardAskResponse | null;
  state: AskState;
  errorMessage: string | null;
  selectedEntryId?: number | null;
  selectedEntry?: ClipboardEntry | null;
}

const loadSavedSession = (): PersistedAskSession => {
  try {
    if (typeof window !== 'undefined' && window.sessionStorage) {
      const raw = window.sessionStorage.getItem(ASK_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        return {
          question: typeof parsed.question === 'string' ? parsed.question : '',
          result: parsed.result || null,
          state: parsed.state === 'loading' ? 'idle' : (parsed.state || 'idle'),
          errorMessage: parsed.errorMessage || null,
          selectedEntryId: parsed.selectedEntryId ?? null,
          selectedEntry: parsed.selectedEntry ?? null,
        };
      }
    }
  } catch {
    // Ignore storage parse errors
  }
  return {
    question: '',
    result: null,
    state: 'idle',
    errorMessage: null,
    selectedEntryId: null,
    selectedEntry: null,
  };
};

export const AskClipboardProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const initialSession = loadSavedSession();
  const [question, setQuestionState] = useState<string>(initialSession.question);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [state, setStateValue] = useState<AskState>(initialSession.state);
  const [result, setResultState] = useState<ClipboardAskResponse | null>(initialSession.result);
  const [errorMessage, setErrorMessageState] = useState<string | null>(initialSession.errorMessage);
  const [copiedAnswer, setCopiedAnswer] = useState<boolean>(false);
  const [historyEntries, setHistoryEntries] = useState<ClipboardEntry[]>([]);
  const [entriesLoaded, setEntriesLoaded] = useState<boolean>(false);
  const [selectedEntryId, setSelectedEntryIdState] = useState<number | null>(initialSession.selectedEntryId ?? null);
  const [selectedEntry, setSelectedEntryState] = useState<ClipboardEntry | null>(initialSession.selectedEntry ?? null);

  const persist = (updated: Partial<PersistedAskSession>) => {
    try {
      if (typeof window !== 'undefined' && window.sessionStorage) {
        const current = loadSavedSession();
        const merged = { ...current, ...updated };
        window.sessionStorage.setItem(ASK_STORAGE_KEY, JSON.stringify(merged));
      }
    } catch {
      // Ignore storage write errors
    }
  };

  const setQuestion = (q: string) => {
    setQuestionState(q);
    persist({ question: q });
  };

  const setState = (s: AskState) => {
    setStateValue(s);
    persist({ state: s });
  };

  const setResult = (r: ClipboardAskResponse | null) => {
    setResultState(r);
    persist({ result: r });
  };

  const setErrorMessage = (msg: string | null) => {
    setErrorMessageState(msg);
    persist({ errorMessage: msg });
  };

  const setSelectedEntryId = (id: number | null) => {
    setSelectedEntryIdState(id);
    persist({ selectedEntryId: id });
  };

  const setSelectedEntry = (entry: ClipboardEntry | null) => {
    setSelectedEntryState(entry);
    persist({ selectedEntry: entry });
  };

  const clearAskState = () => {
    setQuestionState('');
    setValidationError(null);
    setStateValue('idle');
    setResultState(null);
    setErrorMessageState(null);
    setCopiedAnswer(false);
    setSelectedEntryIdState(null);
    setSelectedEntryState(null);
    try {
      if (typeof window !== 'undefined' && window.sessionStorage) {
        window.sessionStorage.removeItem(ASK_STORAGE_KEY);
      }
    } catch {
      // Ignore storage remove errors
    }
  };

  return (
    <AskClipboardContext.Provider
      value={{
        question,
        setQuestion,
        validationError,
        setValidationError,
        state,
        setState,
        result,
        setResult,
        errorMessage,
        setErrorMessage,
        copiedAnswer,
        setCopiedAnswer,
        historyEntries,
        setHistoryEntries,
        entriesLoaded,
        setEntriesLoaded,
        selectedEntryId,
        setSelectedEntryId,
        selectedEntry,
        setSelectedEntry,
        clearAskState,
      }}
    >
      {children}
    </AskClipboardContext.Provider>
  );
};

export const useAskClipboard = (): AskClipboardContextType => {
  const context = useContext(AskClipboardContext);
  if (!context) {
    throw new Error('useAskClipboard must be used within an AskClipboardProvider');
  }
  return context;
};
