import { useCallback, useEffect, useRef, useState } from 'react';

interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: ArrayLike<{
    isFinal: boolean;
    0: { transcript: string };
  }>;
}

interface VoiceCommandResult {
  supported: boolean;
  listening: boolean;
  error: string | null;
  transcript: string;
  start: () => void;
  stop: () => void;
  toggle: () => void;
}

export type { SpeechRecognitionLike };

function getSpeechRecognitionCtor(): (new () => SpeechRecognitionLike) | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as Record<string, unknown>;
  return (w.SpeechRecognition as (new () => SpeechRecognitionLike) | undefined) ??
    (w.webkitSpeechRecognition as (new () => SpeechRecognitionLike) | undefined) ??
    null;
}

export function useVoiceCommand(onCommand: (transcript: string) => void): VoiceCommandResult {
  const [listening, setListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [error, setError] = useState<string | null>(null);

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const onCommandRef = useRef(onCommand);
  const supportedRef = useRef(false);
  const [supported, setSupported] = useState(false);

  onCommandRef.current = onCommand;

  useEffect(() => {
    const Ctor = getSpeechRecognitionCtor();
    supportedRef.current = !!Ctor;
    setSupported(!!Ctor);
    if (!Ctor) return;

    const recognition = new Ctor();
    recognition.lang = 'en-US';
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;

    recognition.onresult = (event) => {
      let latest = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          latest = result[0].transcript.trim();
          if (latest) onCommandRef.current(latest);
        }
      }
      if (latest) {
        setTranscript(latest);
      }
    };

    recognition.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setError('Microphone access denied');
      } else if (event.error !== 'aborted' && event.error !== 'no-speech') {
        setError(event.error);
      }
    };

    recognition.onend = () => {
      setListening(false);
    };

    recognitionRef.current = recognition;

    return () => {
      try {
        recognition.stop();
      } catch {
        // no-op
      }
      recognitionRef.current = null;
    };
  }, []);

  const start = useCallback(() => {
    if (!supportedRef.current) {
      setError('Speech recognition is not supported in this browser');
      return;
    }
    setError(null);
    try {
      recognitionRef.current?.start();
      setListening(true);
    } catch {
      setError('Could not start speech recognition');
    }
  }, []);

  const stop = useCallback(() => {
    try {
      recognitionRef.current?.stop();
    } catch {
      // no-op
    }
    setListening(false);
  }, []);

  const toggle = useCallback(() => {
    if (listening) {
      stop();
    } else {
      start();
    }
  }, [listening, start, stop]);

  return { supported, listening, error, transcript, start, stop, toggle };
}
