package com.nexus.oms.service;

import com.nexus.oms.entity.NxEdiDocument;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.EdiDocumentRepository;
import com.nexus.oms.repository.EdiPartnerRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EdiAutomationServiceTest {

    @Mock private EdiDocumentRepository ediDocumentRepository;
    @Mock private EdiPartnerRepository ediPartnerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private AsnService asnService;

    private EdiAutomationService service;
    private UUID tenantId;

    private static final String EDI_850 =
            "ISA*00**00**ZZ*PARTNER**000000123~\n" +
            "GS*PO*PARTNER*NEXUS*20260101*1200*1*X*004010~\n" +
            "ST*850*0001~\n" +
            "BEG*00*SA*PO-12345**20260101~\n" +
            "N1*BY*Acme Corp*92*ACME001~\n" +
            "PO1*1*10*EA*12.50*UP*123456789012~\n" +
            "PO1*2*5*EA*8.00*UP*098765432109~\n" +
            "SE*10*0001~";

    private static final String EDI_856 =
            "ST*856*0001~\n" +
            "BSN*00*SN-987*20260101*120000~\n" +
            "HL*1**S~\n" +
            "MAN*GM*SN-1~\n" +
            "TD1*2*PLT~\n" +
            "TD5*B*FDX*FedEx*PRIORITY~\n" +
            "TD3*BOX*TRACK123*PACK1~\n" +
            "LIN*1*UP*123456789012~\n" +
            "SN1*1*25*EA~\n" +
            "LIN*2*UP*098765432109~\n" +
            "SN1*2*10*EA~\n" +
            "SE*10*0001~";

    private static final String EDI_810 =
            "ST*810*0001~\n" +
            "BIG*20260101*INV-555*PO-12345~\n" +
            "IT1*1*2*EA*12.50*UP*123456789012~\n" +
            "TDS*2500~\n" +
            "SE*5*0001~";

    private static final String EDI_856_BULK =
            "ST*856*0001~\n" +
            "BSN*00*SN-1001*20260101*120000~\n" +
            "TD5*B*FDX*FedEx*PRIORITY~\n" +
            "LIN*1*UP*111111111111~\n" +
            "SN1*1*12*EA~\n" +
            "SE*6*0001~\n" +
            "ST*856*0002~\n" +
            "BSN*00*SN-1002*20260102*090000~\n" +
            "TD5*B*UPS*UPS*NEXT_DAY~\n" +
            "LIN*1*UP*222222222222~\n" +
            "SN1*1*7*EA~\n" +
            "SE*6*0002~";

    private static final String EDI_940 =
            "ST*940*0001~\n" +
            "W05*SHIP-777*20260103~\n" +
            "N1*SF*Acme Corp*92*ACME001~\n" +
            "N1*ST*Nexus WH*92*NEXUS01~\n" +
            "TD5*B*UPS*UPS*\n" +
            "LIN*1*UP*333333333333~\n" +
            "QTY*1*8*EA~\n" +
            "SE*8*0001~";

    @BeforeEach
    void setUp() {
        service = new EdiAutomationService(ediDocumentRepository, ediPartnerRepository, orderRepository, asnService);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void dryRun_850_parsesPurchaseOrderAndLineItems() {
        Map<String, Object> result = service.dryRun(EDI_850, "850");

        assertEquals("850", result.get("docType"));
        assertEquals(true, result.get("valid"));
        Map<?, ?> data = (Map<?, ?>) result.get("parsedData");
        assertEquals("PO-12345", data.get("purchaseOrderNumber"));
        assertEquals("Acme Corp", data.get("partnerName"));
        assertEquals("ACME001", data.get("partnerId"));
        assertEquals(2, ((java.util.List<?>) data.get("items")).size());
        Map<?, ?> orderData = (Map<?, ?>) data.get("orderData");
        assertEquals("EDI", orderData.get("channel"));
        assertEquals("PO-12345", orderData.get("channelOrderId"));
    }

    @Test
    void dryRun_856_parsesShipNoticeAndTracking() {
        Map<String, Object> result = service.dryRun(EDI_856, "856");

        assertEquals(true, result.get("valid"));
        Map<?, ?> data = (Map<?, ?>) result.get("parsedData");
        assertEquals("SN-987", data.get("shipNoticeNumber"));
        assertEquals("TRACK123", data.get("trackingNumber"));
        assertEquals("FedEx", data.get("carrierName"));
        assertEquals(1, ((java.util.List<?>) data.get("packages")).size());
        java.util.List<?> items = (java.util.List<?>) data.get("items");
        assertEquals(2, items.size());
    }

    @Test
    void dryRun_856_extractsLinSn1LineItems() {
        Map<String, Object> result = service.dryRun(EDI_856, "856");

        Map<?, ?> data = (Map<?, ?>) result.get("parsedData");
        java.util.List<Map<?, ?>> items = (java.util.List<Map<?, ?>>) data.get("items");
        assertEquals("123456789012", items.get(0).get("productId"));
        assertEquals("25", items.get(0).get("quantity"));
        assertEquals("098765432109", items.get(1).get("productId"));
        assertEquals("10", items.get(1).get("quantity"));
    }

    @Test
    void uploadAndParse_856_createsAsnAndLinksDocument() {
        when(ediDocumentRepository.save(any())).thenAnswer(inv -> {
            NxEdiDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        com.nexus.oms.entity.NxAsn created = new com.nexus.oms.entity.NxAsn();
        created.setId(UUID.randomUUID());
        created.setAsnNumber("SN-987");
        when(asnService.createAsnFromEdi(eq(tenantId), any(), any())).thenReturn(created);

        NxEdiDocument doc = service.uploadAndParse("test.856", EDI_856, "856");

        assertEquals("PARSED", doc.getParsedStatus());
        assertEquals(created.getId(), doc.getAsnId());
        verify(asnService).createAsnFromEdi(eq(tenantId), any(), eq(doc.getId()));
    }

    @Test
    void dryRun_856_bulkParsesEveryShipment() {
        Map<String, Object> result = service.dryRun(EDI_856_BULK, "856");

        assertEquals(true, result.get("valid"));
        Map<?, ?> data = (Map<?, ?>) result.get("parsedData");
        assertEquals(true, data.get("bulk"));
        assertEquals(2, data.get("shipmentCount"));
        java.util.List<Map<?, ?>> shipments = (java.util.List<Map<?, ?>>) data.get("shipments");
        assertEquals(2, shipments.size());
        assertEquals("SN-1001", shipments.get(0).get("shipNoticeNumber"));
        assertEquals("SN-1002", shipments.get(1).get("shipNoticeNumber"));
        assertEquals("UPS", shipments.get(1).get("carrierName"));
        java.util.List<?> secondItems = (java.util.List<?>) shipments.get(1).get("items");
        assertEquals("222222222222", ((Map<?, ?>) secondItems.get(0)).get("productId"));
    }

    @Test
    void uploadAndParse_856_bulkCreatesAsnPerShipment() {
        when(ediDocumentRepository.save(any())).thenAnswer(inv -> {
            NxEdiDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(asnService.createAsnFromEdi(eq(tenantId), any(), any())).thenAnswer(inv -> {
            com.nexus.oms.entity.NxAsn a = new com.nexus.oms.entity.NxAsn();
            a.setId(UUID.randomUUID());
            return a;
        });

        NxEdiDocument doc = service.uploadAndParse("bulk.856", EDI_856_BULK, "856");

        assertEquals("PARSED", doc.getParsedStatus());
        verify(asnService, times(2)).createAsnFromEdi(eq(tenantId), any(), eq(doc.getId()));
        assertNotNull(doc.getAsnId());
    }

    @Test
    void dryRun_940_parsesWarehouseShippingOrder() {
        Map<String, Object> result = service.dryRun(EDI_940, "940");

        assertEquals(true, result.get("valid"));
        Map<?, ?> data = (Map<?, ?>) result.get("parsedData");
        assertEquals("940", data.get("transactionSet"));
        assertEquals("SHIP-777", data.get("shippingOrderNumber"));
        assertEquals("Acme Corp", data.get("shipFromName"));
        assertEquals(1, ((java.util.List<?>) data.get("items")).size());
        Map<?, ?> item = (Map<?, ?>) ((java.util.List<?>) data.get("items")).get(0);
        assertEquals("333333333333", item.get("productId"));
        assertEquals("8", item.get("quantity"));
    }

    @Test
    void dryRun_810_parsesInvoiceHeaderAndTotal() {
        Map<String, Object> result = service.dryRun(EDI_810, "810");

        assertEquals(true, result.get("valid"));
        Map<?, ?> data = (Map<?, ?>) result.get("parsedData");
        assertEquals("INV-555", data.get("invoiceNumber"));
        assertEquals("2500", data.get("totalInvoiceAmount"));
        assertEquals(1, ((java.util.List<?>) data.get("items")).size());
    }

    @Test
    void dryRun_invalidContentCollectsValidationErrors() {
        Map<String, Object> result = service.dryRun("ST*850*0001\nN1*BY*Acme*92*ACME\n", "850");

        assertEquals(false, result.get("valid"));
        assertFalse(((java.util.List<?>) result.get("errors")).isEmpty());
    }

    @Test
    void dryRun_unsupportedDocTypeThrows() {
        assertThrows(BadRequestException.class, () -> service.dryRun("FOO*BAR~", "997"));
    }

    @Test
    void uploadAndParse_850_parsesAndCreatesRealOrder() {
        when(ediDocumentRepository.save(any())).thenAnswer(inv -> {
            NxEdiDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(orderRepository.save(any())).thenAnswer(inv -> {
            NxOrder o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        NxEdiDocument doc = service.uploadAndParse("po.txt", EDI_850, "850");

        assertEquals("PARSED", doc.getParsedStatus());
        assertNotNull(doc.getOrderId());
        assertNotNull(doc.getInterchangeControlNumber());
        assertNotNull(doc.getControlNumber());

        ArgumentCaptor<NxOrder> captor = ArgumentCaptor.forClass(NxOrder.class);
        verify(orderRepository).save(captor.capture());
        assertEquals("EDI", captor.getValue().getChannel());
        assertEquals("PO-12345", captor.getValue().getChannelOrderId());
        assertEquals("PENDING", captor.getValue().getStatus());
    }

    @Test
    void uploadAndParse_unsupportedDocTypeMarksFailed() {
        when(ediDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxEdiDocument doc = service.uploadAndParse("bad.txt", "FOO*BAR~", "997");

        assertEquals("FAILED", doc.getParsedStatus());
        assertNotNull(doc.getErrorMessage());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void generate997Ack_echoesControlNumbersAndBuildsX12() {
        NxEdiDocument inbound = NxEdiDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .docType("850")
                .filename("po.txt")
                .rawContent(EDI_850)
                .parsedStatus("PARSED")
                .interchangeControlNumber("000000123")
                .groupControlNumber("1")
                .controlNumber("0001")
                .partnerId("PARTNER")
                .partnerName("Acme Corp")
                .build();
        when(ediDocumentRepository.findById(inbound.getId())).thenReturn(java.util.Optional.of(inbound));
        when(ediDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxEdiDocument ack = service.generate997Ack(inbound.getId(), true);

        assertEquals("997", ack.getDocType());
        assertTrue(ack.getRawContent().contains("AK1*850*1"));
        assertTrue(ack.getRawContent().contains("AK2*850*0001"));
        assertTrue(ack.getRawContent().contains("AK9*A"));
        assertTrue(ack.getRawContent().contains("ST*997*0001"));
        assertTrue(ack.getRawContent().contains("IEA*1*000000997"));
    }

    @Test
    void generate997Ack_rejectsAcknowledgingAnAck() {
        NxEdiDocument ack = NxEdiDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .docType("997")
                .filename("ack.txt")
                .rawContent("ST*997*0001~")
                .parsedStatus("PARSED")
                .build();
        when(ediDocumentRepository.findById(ack.getId())).thenReturn(java.util.Optional.of(ack));

        assertThrows(BadRequestException.class, () -> service.generate997Ack(ack.getId(), true));
    }

    @Test
    void generate855Ack_echoesPoNumberAndLineAcks() {
        NxOrder order = NxOrder.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .channel("EDI")
                .channelOrderId("PO-12345")
                .status("PENDING")
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));
        when(ediDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        java.util.List<Map<String, Object>> lineAcks = java.util.List.of(
                Map.of("status", "AC", "quantity", "10", "unitPrice", "12.50"),
                Map.of("status", "BP", "quantity", "2", "unitPrice", "8.00")
        );

        NxEdiDocument ack = service.generate855Ack(order.getId(), "PO-12345", lineAcks);

        assertEquals("855", ack.getDocType());
        assertEquals(order.getId(), ack.getOrderId());
        assertTrue(ack.getRawContent().contains("BAK*00*AC*PO-12345"));
        assertTrue(ack.getRawContent().contains("ACK*AC*10"));
        assertTrue(ack.getRawContent().contains("ACK*BP*2"));
        assertTrue(ack.getRawContent().contains("ST*855*0001"));
        assertTrue(ack.getRawContent().contains("IEA*1*000000855"));
    }

    @Test
    void generate855Ack_throwsWhenOrderMissing() {
        UUID missing = UUID.randomUUID();
        when(orderRepository.findById(missing)).thenReturn(java.util.Optional.empty());

        assertThrows(com.nexus.oms.exception.ResourceNotFoundException.class,
                () -> service.generate855Ack(missing, "PO-999", null));
    }
}
