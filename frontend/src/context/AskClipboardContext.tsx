import React, { createContext, useContext, useState, useRef, useEffect, useCallback, ReactNode } from 'react';
import { ClipboardAskResponse, ClipboardEntry } from '../types/clipboard';
import { useAuth } from '../auth/AuthContext';

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

const LEGACY_STORAGE_KEY_V1 = 'contextclip_ask_session_v1';
const LEGACY_STORAGE_KEY = 'contextclip_ask_session';

const getStorageKey = (username?: string | null): string => {
  return username ? `contextclip_ask_${username}` : 'contextclip_ask_anon';
};

interface PersistedAskSession {
  version: number;
  username: string;
  question: string;
  result: ClipboardAskResponse | null;
}

const loadSavedSession = (username?: string | null): { question: string; result: ClipboardAskResponse | null; state: AskState } => {
  try {
    if (typeof window !== 'undefined') {
      const key = getStorageKey(username);
      let raw = window.localStorage?.getItem(key);
      if (!raw && !username) {
        raw = window.localStorage?.getItem(LEGACY_STORAGE_KEY_V1) || window.sessionStorage?.getItem(LEGACY_STORAGE_KEY);
      }
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed && (parsed.username === username || (!username && !parsed.username))) {
          const res = parsed.result || null;
          return {
            question: typeof parsed.question === 'string' ? parsed.question : '',
            result: res,
            state: res ? 'success' : 'idle',
          };
        }
      }
    }
  } catch {
    // Ignore storage parse errors
  }
  return {
    question: '',
    result: null,
    state: 'idle',
  };
};

export const AskClipboardProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { user, isAuthenticated } = useAuth();
  const currentUsername = user?.username || null;

  const initialSession = loadSavedSession(currentUsername);
  const sessionRef = useRef(initialSession);

  const [question, setQuestionState] = useState<string>(initialSession.question);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [state, setStateValue] = useState<AskState>(initialSession.state);
  const [result, setResultState] = useState<ClipboardAskResponse | null>(initialSession.result);
  const [errorMessage, setErrorMessageState] = useState<string | null>(null);
  const [copiedAnswer, setCopiedAnswer] = useState<boolean>(false);
  const [historyEntries, setHistoryEntries] = useState<ClipboardEntry[]>([]);
  const [entriesLoaded, setEntriesLoaded] = useState<boolean>(false);
  const [selectedEntryId, setSelectedEntryIdState] = useState<number | null>(null);
  const [selectedEntry, setSelectedEntryState] = useState<ClipboardEntry | null>(null);

  const persist = useCallback((q: string, res: ClipboardAskResponse | null) => {
    try {
      if (typeof window !== 'undefined' && currentUsername) {
        const key = getStorageKey(currentUsername);
        const data: PersistedAskSession = {
          version: 1,
          username: currentUsername,
          question: q,
          result: res,
        };
        window.localStorage?.setItem(key, JSON.stringify(data));
      }
    } catch {
      // Ignore storage write errors
    }
  }, [currentUsername]);

  const clearAskState = useCallback(() => {
    sessionRef.current = { question: '', result: null, state: 'idle' };
    setQuestionState('');
    setValidationError(null);
    setStateValue('idle');
    setResultState(null);
    setErrorMessageState(null);
    setCopiedAnswer(false);
    setSelectedEntryIdState(null);
    setSelectedEntryState(null);
    try {
      if (typeof window !== 'undefined') {
        if (currentUsername) {
          window.localStorage?.removeItem(getStorageKey(currentUsername));
        }
        window.localStorage?.removeItem(LEGACY_STORAGE_KEY_V1);
        window.sessionStorage?.removeItem(LEGACY_STORAGE_KEY);
      }
    } catch {
      // Ignore storage remove errors
    }
  }, [currentUsername]);

  // Reset state whenever authenticated user changes or logs out
  const prevUserRef = useRef(currentUsername);
  useEffect(() => {
    if (prevUserRef.current !== currentUsername) {
      prevUserRef.current = currentUsername;
      if (!isAuthenticated || !currentUsername) {
        clearAskState();
        setHistoryEntries([]);
        setEntriesLoaded(false);
      } else {
        const restored = loadSavedSession(currentUsername);
        sessionRef.current = restored;
        setQuestionState(restored.question);
        setResultState(restored.result);
        setStateValue(restored.state);
        setErrorMessageState(null);
        setValidationError(null);
        setHistoryEntries([]);
        setEntriesLoaded(false);
      }
    }
  }, [currentUsername, isAuthenticated, clearAskState]);

  // Listen for logout / unauthorized events to immediately wipe state
  useEffect(() => {
    const handleLogout = () => {
      clearAskState();
      setHistoryEntries([]);
      setEntriesLoaded(false);
    };

    window.addEventListener('contextclip:auth-logout', handleLogout);
    window.addEventListener('auth:unauthorized', handleLogout);
    return () => {
      window.removeEventListener('contextclip:auth-logout', handleLogout);
      window.removeEventListener('auth:unauthorized', handleLogout);
    };
  }, [clearAskState]);

  const setQuestion = (q: string) => {
    setQuestionState(q);
    sessionRef.current.question = q;
    persist(q, result);
  };

  const setState = (s: AskState) => {
    setStateValue(s);
  };

  const setResult = (r: ClipboardAskResponse | null) => {
    setResultState(r);
    sessionRef.current.result = r;
    persist(question, r);
  };

  const setErrorMessage = (msg: string | null) => {
    setErrorMessageState(msg);
  };

  const setSelectedEntryId = (id: number | null) => {
    setSelectedEntryIdState(id);
  };

  const setSelectedEntry = (entry: ClipboardEntry | null) => {
    setSelectedEntryState(entry);
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
