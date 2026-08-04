import { useEffect, useRef, useCallback, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { useAuth } from '../context/AuthContext';

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'error';

export interface WebSocketMessage {
  type: string;
  timestamp: string;
  [key: string]: unknown;
}

export interface UseWebSocketOptions {
  onOrderUpdate?: (message: WebSocketMessage) => void;
  onInventoryAlert?: (message: WebSocketMessage) => void;
  onShipmentUpdate?: (message: WebSocketMessage) => void;
  onSystemAlert?: (message: WebSocketMessage) => void;
  onDashboardUpdate?: (message: WebSocketMessage) => void;
  onUserStatus?: (message: WebSocketMessage) => void;
  onNotification?: (message: WebSocketMessage) => void;
  autoConnect?: boolean;
}

const WS_ENDPOINT = `${window.location.protocol}//${window.location.host}/api/v1/ws`;

export const useWebSocket = (options: UseWebSocketOptions = {}) => {
  const { token } = useAuth();
  const stompClient = useRef<Client | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const reconnectAttempts = useRef(0);
  const maxReconnectAttempts = 3;
  const mountedRef = useRef(true);
  const disconnectingRef = useRef(false);
  const optionsRef = useRef(options);
  const tokenRef = useRef(token);
  const connectRef = useRef<() => void>();
  const autoConnectRef = useRef(options.autoConnect);
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [error, setError] = useState<string | null>(null);

  optionsRef.current = options;
  tokenRef.current = token;
  autoConnectRef.current = options.autoConnect;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const subscribe = useCallback((client: Client) => {
    const opts = optionsRef.current;
    client.subscribe('/topic/orders', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      opts.onOrderUpdate?.(payload);
    });
    client.subscribe('/topic/inventory', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      opts.onInventoryAlert?.(payload);
    });
    client.subscribe('/topic/shipments', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      opts.onShipmentUpdate?.(payload);
    });
    client.subscribe('/topic/system', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      opts.onSystemAlert?.(payload);
    });
    client.subscribe('/topic/dashboard', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      opts.onDashboardUpdate?.(payload);
    });
    client.subscribe('/topic/users', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      opts.onUserStatus?.(payload);
    });
    client.subscribe('/user/queue/notifications', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      opts.onNotification?.(payload);
    });
  }, []);

  const disconnect = useCallback(() => {
    disconnectingRef.current = true;
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = undefined;
    }
    reconnectAttempts.current = 0;
    if (stompClient.current) {
      stompClient.current.deactivate();
      stompClient.current = null;
    }
    setStatus('disconnected');
    setError(null);
  }, []);

  const connect = useCallback(() => {
    if (!tokenRef.current) return;
    setStatus('connecting');
    setError(null);
    disconnectingRef.current = false;

    try {

      const client = new Client({
        connectHeaders: { Authorization: `Bearer ${tokenRef.current}` },
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          if (!mountedRef.current) return;
          setStatus('connected');
          setError(null);
          reconnectAttempts.current = 0;
          subscribe(client);
        },
        onStompError: () => {
          if (!mountedRef.current) return;
          setError('STOMP protocol error');
          setStatus('error');
        },
        onWebSocketError: () => {
          if (!mountedRef.current) return;
          setError('WebSocket transport unavailable');
          setStatus('error');
        },
        onWebSocketClose: () => {
          if (!mountedRef.current) return;
          setStatus('disconnected');
        },
        onDisconnect: () => {
          if (!mountedRef.current) return;
          setStatus('disconnected');
        },
      });

      client.webSocketFactory = () => new SockJS(WS_ENDPOINT) as unknown as WebSocket;

      client.activate();
      stompClient.current = client;
    } catch {
      if (!mountedRef.current) return;
      setStatus('error');
      setError('Failed to create connection');
    }
  }, [subscribe]);

  connectRef.current = connect;

  const sendMessage = useCallback((destination: string, message: Record<string, unknown>) => {
    if (stompClient.current && stompClient.current.connected) {
      stompClient.current.send(destination, {}, JSON.stringify(message));
    }
  }, []);

  useEffect(() => {
    if (autoConnectRef.current !== false && tokenRef.current) {
      connectRef.current?.();
    }
    return () => {
      disconnect();
    };
  }, [disconnect]);

  return {
    status,
    isConnected: status === 'connected',
    error,
    lastMessage,
    connect,
    disconnect,
    sendMessage,
  };
};
