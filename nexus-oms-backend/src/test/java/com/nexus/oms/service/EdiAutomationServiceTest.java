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
}
