package com.nexus.oms.security;

import java.util.UUID;

public record TenantAwarePrincipal(String username, UUID tenantId) {}
