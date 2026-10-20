package com.nexus.oms.service;

import com.nexus.oms.dto.AsnRequest;
import com.nexus.oms.entity.NxAsn;
import com.nexus.oms.entity.NxAsnLine;
import com.nexus.oms.entity.NxInventoryReceipt;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxAsnLineRepository;
import com.nexus.oms.repository.NxAsnRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsnServiceTest {

    @Mock private NxAsnRepository asnRepository;
    @Mock private NxAsnLineRepository asnLineRepository;
    @Mock private InventoryReceiptService inventoryReceiptService;
    @Mock private SlottingService slottingService;

    private AsnService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new AsnService(asnRepository, asnLineRepository, inventoryReceiptService, slottingService);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AsnRequest request() {
        AsnRequest req = new AsnRequest();
        req.setAsnNumber("ASN-1001");
        req.setNodeId(UUID.randomUUID());
        req.setSupplierName("Acme Corp");
        req.setCarrierCode("FDX");
        req.setTrackingNumber("TRACK1");
        req.setPurchaseOrderNumber("PO-12345");
        AsnRequest.AsnLineRequest line = new AsnRequest.AsnLineRequest();
        line.setSku("SKU-1");
        line.setProductName("Widget");
        line.setExpectedQty(10);
        req.setLines(List.of(line));
        return req;
    }

    private NxAsn openAsn() {
        NxAsn asn = NxAsn.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .asnNumber("ASN-1001").nodeId(UUID.randomUUID())
                .status("OPEN").source("MANUAL").build();
        NxAsnLine line = NxAsnLine.builder()
                .id(UUID.randomUUID()).asn(asn).tenantId(tenantId)
                .sku("SKU-1").productName("Widget")
                .expectedQty(10).receivedQty(0).status("PENDING").build();
        asn.getLines().add(line);
        return asn;
    }

    @Test
    void createAsn_createsHeaderAndLines() {
        when(asnRepository.existsByTenantIdAndAsnNumber(tenantId, "ASN-1001")).thenReturn(false);
        when(asnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAsn asn = service.createAsn(tenantId, request());

        assertEquals("ASN-1001", asn.getAsnNumber());
        assertEquals("OPEN", asn.getStatus());
        assertEquals("MANUAL", asn.getSource());
        assertEquals(1, asn.getLines().size());
        assertEquals(10, asn.getLines().get(0).getExpectedQty());
        verify(asnRepository).save(any());
    }

    @Test
    void createAsn_duplicateNumberThrows() {
        when(asnRepository.existsByTenantIdAndAsnNumber(tenantId, "ASN-1001")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.createAsn(tenantId, request()));
        verify(asnRepository, never()).save(any());
    }

    @Test
    void createAsn_negativeExpectedQtyThrows() {
        when(asnRepository.existsByTenantIdAndAsnNumber(tenantId, "ASN-1001")).thenReturn(false);
        AsnRequest req = request();
        req.getLines().get(0).setExpectedQty(0);

        assertThrows(BadRequestException.class, () -> service.createAsn(tenantId, req));
    }

    @Test
    void receiveLine_happyPathCreatesReceiptAndUpdatesLine() {
        NxAsn asn = openAsn();
        when(asnRepository.findById(asn.getId())).thenReturn(Optional.of(asn));
        NxInventoryReceipt receipt = NxInventoryReceipt.builder().id(UUID.randomUUID()).build();
        when(inventoryReceiptService.createReceipt(eq(tenantId), any())).thenReturn(receipt);
        when(asnLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAsn result = service.receiveLine(asn.getId(), asn.getLines().get(0).getId(), 10, "testuser");

        assertEquals("COMPLETE", result.getStatus());
        assertEquals(10, result.getLines().get(0).getReceivedQty());
        assertEquals("RECEIVED", result.getLines().get(0).getStatus());
        assertEquals("testuser", result.getReceivedBy());
        verify(inventoryReceiptService).receiveInventory(receipt.getId(), "testuser");
        verify(slottingService).recommendPutaway(eq("SKU-1"), eq(asn.getNodeId()), eq(10), eq("testuser"));
    }

    @Test
    void receiveLine_partialReceiveKeepsAsnOpen() {
        NxAsn asn = openAsn();
        when(asnRepository.findById(asn.getId())).thenReturn(Optional.of(asn));
        NxInventoryReceipt receipt = NxInventoryReceipt.builder().id(UUID.randomUUID()).build();
        when(inventoryReceiptService.createReceipt(eq(tenantId), any())).thenReturn(receipt);
        when(asnLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAsn result = service.receiveLine(asn.getId(), asn.getLines().get(0).getId(), 4, "testuser");

        assertEquals("OPEN", result.getStatus());
        assertEquals("PARTIAL", result.getLines().get(0).getStatus());
        assertNull(result.getReceivedBy());
    }

    @Test
    void receiveLine_overReceiptBeyondToleranceThrows() {
        NxAsn asn = openAsn();
        when(asnRepository.findById(asn.getId())).thenReturn(Optional.of(asn));

        assertThrows(BadRequestException.class,
                () -> service.receiveLine(asn.getId(), asn.getLines().get(0).getId(), 12, "testuser"));
        verify(inventoryReceiptService, never()).createReceipt(any(), any());
    }

    @Test
    void receiveLine_unknownLineThrows() {
        NxAsn asn = openAsn();
        when(asnRepository.findById(asn.getId())).thenReturn(Optional.of(asn));

        assertThrows(ResourceNotFoundException.class,
                () -> service.receiveLine(asn.getId(), UUID.randomUUID(), 1, "testuser"));
    }

    @Test
    void receiveLine_closedAsnThrows() {
        NxAsn asn = openAsn();
        asn.setStatus("CLOSED");
        when(asnRepository.findById(asn.getId())).thenReturn(Optional.of(asn));

        assertThrows(IllegalStateException.class,
                () -> service.receiveLine(asn.getId(), asn.getLines().get(0).getId(), 1, "testuser"));
    }

    @Test
    void receiveLine_missingNodeThrows() {
        NxAsn asn = openAsn();
        asn.setNodeId(null);
        when(asnRepository.findById(asn.getId())).thenReturn(Optional.of(asn));

        assertThrows(BadRequestException.class,
                () -> service.receiveLine(asn.getId(), asn.getLines().get(0).getId(), 1, "testuser"));
        verify(inventoryReceiptService, never()).createReceipt(any(), any());
    }

    @Test
    void closeAsn_marksClosed() {
        NxAsn asn = openAsn();
        when(asnRepository.findById(asn.getId())).thenReturn(Optional.of(asn));
        when(asnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAsn result = service.closeAsn(asn.getId());

        assertEquals("CLOSED", result.getStatus());
    }

    @Test
    void createAsnFromEdi_ignoresUnknownSkusAndEmptyItems() {
        assertNull(service.createAsnFromEdi(tenantId, Map.of(), UUID.randomUUID()));
        assertNull(service.createAsnFromEdi(tenantId,
                Map.of("shipNoticeNumber", "SN-1", "items", List.of(Map.of("quantity", "5"))),
                UUID.randomUUID()));
        verify(asnRepository, never()).save(any());
    }

    @Test
    void createAsnFromEdi_createsAsnFromParsedData() {
        when(asnRepository.existsByTenantIdAndAsnNumber(tenantId, "SN-987")).thenReturn(false);
        when(asnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAsn asn = service.createAsnFromEdi(tenantId, Map.of(
                "shipNoticeNumber", "SN-987",
                "purchaseOrderNumber", "PO-12345",
                "carrierCode", "FDX",
                "trackingNumber", "TRACK123",
                "items", List.of(
                        Map.of("productId", "123456789012", "quantity", "25"),
                        Map.of("productId", "098765432109", "quantity", "10"))),
                UUID.randomUUID());

        assertEquals("SN-987", asn.getAsnNumber());
        assertEquals("EDI_856", asn.getSource());
        assertEquals(2, asn.getLines().size());
        assertEquals("OPEN", asn.getStatus());
        verify(asnRepository).save(any());
    }

    @Test
    void createAsnFromEdi_duplicateReturnsExisting() {
        NxAsn existing = openAsn();
        when(asnRepository.existsByTenantIdAndAsnNumber(tenantId, "ASN-1001")).thenReturn(true);
        when(asnRepository.findByTenantIdAndAsnNumber(tenantId, "ASN-1001")).thenReturn(Optional.of(existing));

        NxAsn result = service.createAsnFromEdi(tenantId,
                Map.of("shipNoticeNumber", "ASN-1001", "items",
                        List.of(Map.of("productId", "SKU-1", "quantity", "5"))),
                UUID.randomUUID());

        assertEquals(existing.getId(), result.getId());
        verify(asnRepository, never()).save(any());
    }

    @Test
    void deleteAsn_delegates() {
        service.deleteAsn(UUID.randomUUID());
        verify(asnRepository).deleteById(any());
    }
}
