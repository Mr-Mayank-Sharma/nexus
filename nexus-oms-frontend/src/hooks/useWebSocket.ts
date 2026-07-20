import { useEffect, useRef, useCallback, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from 'stompjs';
import { useAuth } from '../context/AuthContext';

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
  const reconnectTimeoutRef = useRef<NodeJS.Timeout>();
  const reconnectAttempts = useRef(0);
  const maxReconnectAttempts = 5;
  const [isConnected, setIsConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);

  const connect = useCallback(() => {
    if (!token) return;

    try {
      const wsUrl = `${window.location.protocol}//${window.location.host}/api/v1/ws`;
      const socket = new SockJS(wsUrl);
      const client = new Client();

      client.configure({
        webSocketFactory: () => socket,
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        reconnectDelay: 5000,
        onConnect: () => {
          console.log('WebSocket connected');
          setIsConnected(true);
          reconnectAttempts.current = 0;

          // Subscribe to topics
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

          // Subscribe to user-specific notifications
          client.subscribe('/user/queue/notifications', (message) => {
            const payload = JSON.parse(message.body) as WebSocketMessage;
            setLastMessage(payload);
            options.onNotification?.(payload);
          });
        },
        onStompError: (frame) => {
          console.error('WebSocket error:', frame.headers['message']);
          setIsConnected(false);
          
          if (reconnectAttempts.current < maxReconnectAttempts) {
            reconnectAttempts.current++;
            const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
            reconnectTimeoutRef.current = setTimeout(connect, delay);
          }
        },
        onDisconnect: () => {
          console.log('WebSocket disconnected');
          setIsConnected(false);
        },
      });

      client.activate();
      stompClient.current = client;
    } catch (error) {
      console.error('WebSocket connection failed:', error);
    }
  }, [token, options]);

  const disconnect = useCallback(() => {
    if (stompClient.current) {
      stompClient.current.deactivate();
      stompClient.current = null;
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }
    setIsConnected(false);
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
    isConnected,
    lastMessage,
    connect,
    disconnect,
    sendMessage,
  };
};
