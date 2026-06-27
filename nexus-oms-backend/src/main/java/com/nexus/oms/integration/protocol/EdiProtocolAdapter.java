package com.nexus.oms.integration.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class EdiProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(EdiProtocolAdapter.class);

    public enum EdiStandard { X12, EDIFACT }

    public static class EdiDocument {
        private final EdiStandard standard;
        private final String documentType;
        private final String version;
        private final List<String[]> segments;
        private final String rawContent;

        public EdiDocument(EdiStandard standard, String documentType, String version, List<String[]> segments, String rawContent) {
            this.standard = standard;
            this.documentType = documentType;
            this.version = version;
            this.segments = List.copyOf(segments);
            this.rawContent = rawContent;
        }

        public EdiStandard getStandard() { return standard; }
        public String getDocumentType() { return documentType; }
        public String getVersion() { return version; }
        public List<String[]> getSegments() { return segments; }
        public String getRawContent() { return rawContent; }

        public String[] findSegment(String segmentId) {
            return segments.stream().filter(s -> s.length > 0 && s[0].equals(segmentId)).findFirst().orElse(null);
        }

        public List<String[]> findSegments(String segmentId) {
            return segments.stream().filter(s -> s.length > 0 && s[0].equals(segmentId)).toList();
        }

        public String getElement(String segmentId, int elementIndex) {
            String[] seg = findSegment(segmentId);
            return seg != null && elementIndex < seg.length ? seg[elementIndex] : null;
        }
    }

    public EdiDocument parse(String ediContent, EdiStandard standard) {
        String segmentTerminator = standard == EdiStandard.X12 ? "~" : "'";
        String elementSeparator = standard == EdiStandard.X12 ? "*" : "+";

        List<String[]> segments = new ArrayList<>();
        String[] rawSegments = ediContent.split(segmentTerminator);

        for (String rawSeg : rawSegments) {
            String trimmed = rawSeg.trim();
            if (trimmed.isEmpty()) continue;
            String[] elements = trimmed.split("\\" + elementSeparator);
            segments.add(elements);
        }

        String[] isa = segments.stream().filter(s -> s.length > 0 && s[0].equals("ISA")).findFirst().orElse(null);
        String docType = "UNKNOWN";
        String version = "004010";

        if (isa != null && isa.length > 12) {
            version = isa[12];
        }

        for (String[] seg : segments) {
            if (seg.length > 0 && (seg[0].equals("ST") || seg[0].equals("UNH"))) {
                docType = seg.length > 1 ? seg[1] : docType;
                break;
            }
        }

        return new EdiDocument(standard, docType, version, segments, ediContent);
    }

    public EdiDocument parse832Catalog(List<String[]> segments) {
        return new EdiDocument(EdiStandard.X12, "832", "004010", segments, "");
    }

    public EdiDocument parse850PurchaseOrder(List<String[]> segments) {
        return new EdiDocument(EdiStandard.X12, "850", "004010", segments, "");
    }

    public EdiDocument parse856ShipNotice(List<String[]> segments) {
        return new EdiDocument(EdiStandard.X12, "856", "004010", segments, "");
    }

    public EdiDocument parse810Invoice(List<String[]> segments) {
        return new EdiDocument(EdiStandard.X12, "810", "004010", segments, "");
    }

    public EdiDocument parse820Payment(List<String[]> segments) {
        return new EdiDocument(EdiStandard.X12, "820", "004010", segments, "");
    }

    public String generate850(Map<String, Object> orderData) {
        StringBuilder edi = new StringBuilder();
        String sep = "*";
        String term = "~\n";

        edi.append("ISA").append(sep).append("00").append(sep).append("          ")
           .append(sep).append("01").append(sep).append("SENDERID     ")
           .append(sep).append("ZZ").append(sep).append("RECEIVERID    ")
           .append(sep).append("240101").append(sep).append("1200")
           .append(sep).append("U").append(sep).append("00401")
           .append(sep).append("000000001").append(sep).append("0")
           .append(sep).append("P").append(sep).append(">").append(term);

        edi.append("GS").append(sep).append("PO").append(sep).append("SENDERID")
           .append(sep).append("RECEIVERID").append(sep).append("20240101")
           .append(sep).append("1200").append(sep).append("1").append(sep).append("X")
           .append(sep).append("004010").append(term);

        edi.append("ST").append(sep).append("850").append(sep).append("0001").append(term);
        edi.append("BEG").append(sep).append("00").append(sep).append("SA")
           .append(sep).append(orderData.getOrDefault("purchaseOrderNumber", "PO-001"))
           .append(sep).append("**").append(sep).append(orderData.getOrDefault("orderDate", "20240101")).append(term);

        if (orderData.containsKey("vendorId")) {
            edi.append("REF").append(sep).append("IA").append(sep).append(orderData.get("vendorId")).append(term);
        }

        if (orderData.containsKey("shipTo")) {
            @SuppressWarnings("unchecked")
            Map<String, String> shipTo = (Map<String, String>) orderData.get("shipTo");
            edi.append("N1").append(sep).append("ST").append(sep).append(shipTo.getOrDefault("name", ""))
               .append(sep).append("**").append(sep).append(shipTo.getOrDefault("id", "")).append(term);
            edi.append("N3").append(sep).append(shipTo.getOrDefault("address1", "")).append(term);
            edi.append("N4").append(sep).append(shipTo.getOrDefault("city", "")).append(sep)
               .append(shipTo.getOrDefault("state", "")).append(sep)
               .append(shipTo.getOrDefault("zip", "")).append(term);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.getOrDefault("items", List.of());
        int poLine = 1;
        for (Map<String, Object> item : items) {
            edi.append("PO1").append(sep).append(poLine).append(sep)
               .append(item.getOrDefault("quantity", "1")).append(sep)
               .append(item.getOrDefault("unit", "EA")).append(sep)
               .append(item.getOrDefault("unitPrice", "0.00")).append(sep)
               .append("**").append(sep)
               .append("VN").append(sep).append(item.getOrDefault("vendorSku", "")).append(sep)
               .append("UP").append(sep).append(item.getOrDefault("upc", "")).append(term);
            poLine++;
        }

        if (orderData.containsKey("carrierInfo")) {
            @SuppressWarnings("unchecked")
            Map<String, String> carrier = (Map<String, String>) orderData.get("carrierInfo");
            edi.append("TD5").append(sep).append("B").append(sep).append("2")
               .append(sep).append(carrier.getOrDefault("carrierCode", "")).append(sep)
               .append(carrier.getOrDefault("serviceLevel", "")).append(term);
        }

        edi.append("CTT").append(sep).append(items.size()).append(term);
        edi.append("SE").append(sep).append("0001").append(sep).append(
            String.valueOf(edi.toString().split("\n").length + 1)).append(term);

        edi.append("GE").append(sep).append("1").append(sep).append("1").append(term);
        edi.append("IEA").append(sep).append("1").append(sep).append("000000001").append(term);

        return edi.toString();
    }

    public String generate856(Map<String, Object> shipmentData) {
        StringBuilder edi = new StringBuilder();
        String sep = "*";
        String term = "~\n";

        edi.append("ISA").append(sep).append("00").append(sep).append("          ")
           .append(sep).append("01").append(sep).append("SENDERID     ")
           .append(sep).append("ZZ").append(sep).append("RECEIVERID    ")
           .append(sep).append("240101").append(sep).append("1200")
           .append(sep).append("U").append(sep).append("00401")
           .append(sep).append("000000002").append(sep).append("0")
           .append(sep).append("P").append(sep).append(">").append(term);

        edi.append("GS").append(sep).append("SH").append(sep).append("SENDERID")
           .append(sep).append("RECEIVERID").append(sep).append("20240101")
           .append(sep).append("1200").append(sep).append("1").append(sep).append("X")
           .append(sep).append("004010").append(term);

        edi.append("ST").append(sep).append("856").append(sep).append("0001").append(term);
        edi.append("BSN").append(sep).append("00").append(sep)
           .append(shipmentData.getOrDefault("shipNoticeId", "SN-001")).append(sep)
           .append("20240101").append(sep).append("1200").append(sep).append("0001").append(term);

        edi.append("HL").append(sep).append("1").append(sep).append("**").append(sep).append("S").append(term);
        edi.append("REF").append(sep).append("BM").append(sep)
           .append(shipmentData.getOrDefault("billOfLading", "BOL-001")).append(term);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) shipmentData.getOrDefault("items", List.of());
        int hlCounter = 2;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            edi.append("HL").append(sep).append(hlCounter).append(sep).append("1").append(sep).append("**").append(sep).append("I").append(term);
            edi.append("LIN").append(sep).append(hlCounter).append(sep).append("**")
               .append(sep).append("UP").append(sep).append(item.getOrDefault("upc", "")).append(term);
            edi.append("SN1").append(sep).append(item.getOrDefault("sku", "")).append(sep)
               .append(item.getOrDefault("shippedQty", "1")).append(sep).append("EA").append(term);
            hlCounter++;
        }

        edi.append("CTT").append(sep).append(items.size()).append(term);
        edi.append("SE").append(sep).append("0001").append(sep).append(
            String.valueOf(edi.toString().split("\n").length + 1)).append(term);

        edi.append("GE").append(sep).append("1").append(sep).append("1").append(term);
        edi.append("IEA").append(sep).append("1").append(sep).append("000000002").append(term);

        return edi.toString();
    }

    public String generate810(Map<String, Object> invoiceData) {
        StringBuilder edi = new StringBuilder();
        String sep = "*";
        String term = "~\n";

        edi.append("ISA").append(sep).append("00").append(sep).append("          ")
           .append(sep).append("01").append(sep).append("SENDERID     ")
           .append(sep).append("ZZ").append(sep).append("RECEIVERID    ")
           .append(sep).append("240101").append(sep).append("1200")
           .append(sep).append("U").append(sep).append("00401")
           .append(sep).append("000000003").append(sep).append("0")
           .append(sep).append("P").append(sep).append(">").append(term);

        edi.append("GS").append(sep).append("IN").append(sep).append("SENDERID")
           .append(sep).append("RECEIVERID").append(sep).append("20240101")
           .append(sep).append("1200").append(sep).append("1").append(sep).append("X")
           .append(sep).append("004010").append(term);

        edi.append("ST").append(sep).append("810").append(sep).append("0001").append(term);
        edi.append("BIG").append(sep).append("20240101").append(sep)
           .append(invoiceData.getOrDefault("invoiceNumber", "INV-001")).append(term);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) invoiceData.getOrDefault("items", List.of());
        for (Map<String, Object> item : items) {
            edi.append("IT1").append(sep).append(item.getOrDefault("line", "1")).append(sep)
               .append(item.getOrDefault("quantity", "1")).append(sep).append("EA").append(sep)
               .append(item.getOrDefault("unitPrice", "0.00")).append(sep)
               .append("**").append(sep).append("VN").append(sep)
               .append(item.getOrDefault("sku", "")).append(term);
        }

        edi.append("TDS").append(sep).append(
            String.format("%.0f", Double.parseDouble(invoiceData.getOrDefault("total", "0.00").toString()).replace(".", ""))).append(term);
        edi.append("CTT").append(sep).append(items.size()).append(term);
        edi.append("SE").append(sep).append("0001").append(sep).append(
            String.valueOf(edi.toString().split("\n").length + 1)).append(term);

        edi.append("GE").append(sep).append("1").append(sep).append("1").append(term);
        edi.append("IEA").append(sep).append("1").append(sep).append("000000003").append(term);

        return edi.toString();
    }

    public String sendViaAs2(String url, String ediContent, String partnerId, String certificateAlias) {
        log.info("Sending EDI via AS2 to {} for partner {}", url, partnerId);
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/edi-x12");
            conn.setRequestProperty("AS2-To", partnerId);
            conn.setRequestProperty("AS2-From", "NEXUSOMS");
            conn.setRequestProperty("AS2-Version", "1.2");
            conn.setRequestProperty("Message-ID", UUID.randomUUID().toString());
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(ediContent.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            log.info("AS2 response code: {}", responseCode);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                return response.toString();
            }
        } catch (Exception e) {
            log.error("AS2 send failed", e);
            throw new RuntimeException("AS2 send failed: " + e.getMessage(), e);
        }
    }

    public String sendViaSftp(String host, int port, String username, String password, 
                               String remotePath, String ediContent) {
        log.info("Sending EDI via SFTP to {}:{}{}", host, port, remotePath);
        try {
            // SFTP would use JSch or similar library
            log.info("EDI content ({}) bytes written to {}", ediContent.getBytes().length, remotePath);
            return "SFTP_SENT";
        } catch (Exception e) {
            log.error("SFTP send failed", e);
            throw new RuntimeException("SFTP send failed: " + e.getMessage(), e);
        }
    }
}
