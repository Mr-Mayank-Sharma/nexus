package com.nexus.oms.config;

import com.nexus.oms.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        if (jwtTokenProvider.validateToken(token)) {
                            String username = jwtTokenProvider.getUsernameFromToken(token);
                            String role = jwtTokenProvider.getRoleFromToken(token);
                            UUID tenantId = jwtTokenProvider.getTenantIdFromToken(token);

                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            username,
                                            null,
                                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                                    );

                            accessor.setUser(authentication);

                            accessor.getSessionAttributes().put("username", username);
                            accessor.getSessionAttributes().put("role", role);
                            accessor.getSessionAttributes().put("tenantId", tenantId.toString());

                            log.debug("WebSocket authenticated for user: {}, role: {}", username, role);
                        } else {
                            log.warn("Invalid JWT token in WebSocket connection");
                            accessor.setLeaveMutable(true);
                            throw new IllegalArgumentException("Invalid token");
                        }
                    } else {
                        log.warn("No Authorization header in WebSocket connection");
                        accessor.setLeaveMutable(true);
                        throw new IllegalArgumentException("Missing token");
                    }
                }
                return message;
            }
        });
    }
}
