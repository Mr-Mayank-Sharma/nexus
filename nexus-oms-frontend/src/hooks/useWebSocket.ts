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

export const useWebSocket = (options: UseWebSocketOptions = {}) => {
  const { token } = useAuth();
  const stompClient = useRef<Client | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const reconnectAttempts = useRef(0);
  const maxReconnectAttempts = 3;
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [error, setError] = useState<string | null>(null);

  const subscribe = useCallback((client: Client) => {
    client.subscribe('/topic/orders', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      options.onOrderUpdate?.(payload);
    });

    client.subscribe('/topic/inventory', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      options.onInventoryAlert?.(payload);
    });

    client.subscribe('/topic/shipments', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      options.onShipmentUpdate?.(payload);
    });

    client.subscribe('/topic/system', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      options.onSystemAlert?.(payload);
    });

    client.subscribe('/topic/dashboard', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      options.onDashboardUpdate?.(payload);
    });

    client.subscribe('/topic/users', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      options.onUserStatus?.(payload);
    });

    client.subscribe('/user/queue/notifications', (message) => {
      const payload = JSON.parse(message.body) as WebSocketMessage;
      setLastMessage(payload);
      options.onNotification?.(payload);
    });
  }, [options]);

  const connect = useCallback(() => {
    if (!token) return;

    setStatus('connecting');
    setError(null);

    try {
      // Use relative URL through Vite proxy (which has ws: true configured)
      // to avoid SockJS connection storms hitting the backend directly.
      // In production, same-origin through reverse proxy.
      const wsUrl = `${window.location.protocol}//${window.location.host}/api/v1/ws`;
      const socket = new SockJS(wsUrl);

      const client = new Client({
        webSocketFactory: () => socket as unknown as WebSocket,
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        reconnectDelay: 5000,
        onConnect: () => {
          setStatus('connected');
          setError(null);
          reconnectAttempts.current = 0;
          subscribe(client);
        },
        onStompError: (frame) => {
          const msg = frame.headers['message'] || 'STOMP protocol error';
          setError(msg);
          setStatus('error');

          if (reconnectAttempts.current < maxReconnectAttempts) {
            reconnectAttempts.current++;
            const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
            reconnectTimeoutRef.current = setTimeout(connect, delay);
          }
        },
        onWebSocketError: () => {
          setError('WebSocket transport unavailable');
          setStatus('error');
          if (reconnectAttempts.current < maxReconnectAttempts) {
            reconnectAttempts.current++;
            const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
            reconnectTimeoutRef.current = setTimeout(connect, delay);
          }
        },
        onWebSocketClose: () => {
          if (status !== 'error') {
            setStatus('disconnected');
          }
        },
        onDisconnect: () => {
          setStatus('disconnected');
        },
      });

      client.activate();
      stompClient.current = client;
    } catch {
      setStatus('error');
      setError('Failed to create connection');
    }
  }, [token, subscribe]);

  const disconnect = useCallback(() => {
    if (stompClient.current) {
      stompClient.current.deactivate();
      stompClient.current = null;
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }
    setStatus('disconnected');
    setError(null);
  }, []);

  const sendMessage = useCallback((destination: string, message: Record<string, unknown>) => {
    if (stompClient.current && stompClient.current.connected) {
      stompClient.current.send(destination, {}, JSON.stringify(message));
    }
  }, []);

  useEffect(() => {
    if (options.autoConnect !== false && token) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [token, connect, disconnect, options.autoConnect]);

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
