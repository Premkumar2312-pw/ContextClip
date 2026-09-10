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

export const AskClipboardProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [question, setQuestion] = useState<string>('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [state, setState] = useState<AskState>('idle');
  const [result, setResult] = useState<ClipboardAskResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [copiedAnswer, setCopiedAnswer] = useState<boolean>(false);
  const [historyEntries, setHistoryEntries] = useState<ClipboardEntry[]>([]);
  const [entriesLoaded, setEntriesLoaded] = useState<boolean>(false);
  const [selectedEntryId, setSelectedEntryId] = useState<number | null>(null);
  const [selectedEntry, setSelectedEntry] = useState<ClipboardEntry | null>(null);

  const clearAskState = () => {
    setQuestion('');
    setValidationError(null);
    setState('idle');
    setResult(null);
    setErrorMessage(null);
    setCopiedAnswer(false);
    setSelectedEntryId(null);
    setSelectedEntry(null);
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
