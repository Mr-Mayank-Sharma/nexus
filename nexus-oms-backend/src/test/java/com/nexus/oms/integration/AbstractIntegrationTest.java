package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxNode;
import com.nexus.oms.entity.RolePermission;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.NodeRepository;
import com.nexus.oms.repository.RolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestMockConfiguration.class)
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected NodeRepository nodeRepository;

    @Autowired
    protected InventoryRepository inventoryRepository;

    @Autowired
    protected RolePermissionRepository rolePermissionRepository;

    protected static String adminToken;
    protected static UUID tenantId;

    protected String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    protected void registerAdminUser() throws Exception {
        long ts = System.currentTimeMillis();
        String username = "testadmin-" + ts;
        String password = "Test1234!";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"username": "%s", "password": "%s", "role": "ADMIN"}
                """.formatted(username, password);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            System.err.println("=== REGISTER FAIL ===");
            System.err.println("Status: " + response.getStatusCode());
            System.err.println("Body: '" + response.getBody() + "'");
        }

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin registration should succeed");

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean(), "Registration response should be successful");

        JsonNode data = json.get("data");
        adminToken = data.get("accessToken").asText();
        tenantId = UUID.fromString(data.get("tenantId").asText());

        assertNotNull(adminToken);
        assertNotNull(tenantId);
    }

    protected HttpHeaders authHeaders() {
        return authHeaders(adminToken);
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    protected UUID initializeWarehouse(String name) {
        NxNode node = NxNode.builder()
                .tenantId(tenantId)
                .name(name != null ? name : "Test Warehouse")
                .type("WAREHOUSE")
                .isActive(true)
                .build();
        return nodeRepository.save(node).getId();
    }

    protected UUID initializeInventory(UUID nodeId, String sku, int qtyOnHand) {
        NxInventory inv = NxInventory.builder()
                .tenantId(tenantId)
                .sku(sku)
                .nodeId(nodeId)
                .quantityOnHand(qtyOnHand)
                .quantityAllocated(0)
                .quantityReserved(0)
                .build();
        return inventoryRepository.save(inv).getId();
    }

    protected void seedPermission(UUID tenantId, String role, String group, String name, boolean canView, boolean canCreate) {
        RolePermission rp = RolePermission.builder()
                .tenantId(tenantId)
                .role(role)
                .permissionGroup(group)
                .permissionName(name)
                .canView(canView)
                .canCreate(canCreate)
                .canEdit(canCreate)
                .canDelete(false)
                .build();
        rolePermissionRepository.save(rp);
    }
}
