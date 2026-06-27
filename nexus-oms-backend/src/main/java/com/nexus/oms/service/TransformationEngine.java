package com.nexus.oms.service;

import com.nexus.oms.entity.IntegrationTransformMapping;
import com.nexus.oms.repository.IntegrationTransformMappingRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TransformationEngine {

    private final IntegrationTransformMappingRepository mappingRepository;

    public TransformationEngine(IntegrationTransformMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    public String transform(String payload, String sourceFormat, String targetFormat, UUID mappingId) {
        if (mappingId != null) {
            IntegrationTransformMapping mapping = mappingRepository.findById(mappingId)
                    .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));
            return applyMapping(payload, mapping);
        }
        String s = sourceFormat.toLowerCase();
        String t = targetFormat.toLowerCase();
        if ("json".equals(s) && "xml".equals(t)) return jsonToXml(payload);
        if ("xml".equals(s) && "json".equals(t)) return xmlToJson(payload);
        if ("csv".equals(s) && "json".equals(t)) return csvToJson(payload);
        if ("edi".equals(s) && "json".equals(t)) return ediToJson(payload);
        throw new UnsupportedOperationException("Transformation from " + sourceFormat + " to " + targetFormat + " not supported");
    }

    public String applyMapping(String payload, IntegrationTransformMapping mapping) {
        return "<transformed>" + payload + "</transformed>";
    }

    public String jsonToXml(String json) {
        return "<root>" + json + "</root>";
    }

    public String xmlToJson(String xml) {
        return "{\"xml\": \"" + xml.replace("\"", "\\\"") + "\"}";
    }

    public String csvToJson(String csv) {
        String[] lines = csv.split("\n");
        if (lines.length < 2) return "[]";
        String[] headers = lines[0].split(",");
        StringBuilder json = new StringBuilder("[");
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(",");
            json.append("{");
            for (int j = 0; j < headers.length && j < values.length; j++) {
                json.append("\"").append(headers[j].trim()).append("\":\"").append(values[j].trim()).append("\"");
                if (j < headers.length - 1 && j < values.length - 1) json.append(",");
            }
            json.append("}");
            if (i < lines.length - 1) json.append(",");
        }
        json.append("]");
        return json.toString();
    }

    public String ediToJson(String edi) {
        return "{\"edi\": \"" + edi.replace("\"", "\\\"") + "\"}";
    }
}
