package com.nexus.oms.integration.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;

@Component
public class SoapProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(SoapProtocolAdapter.class);

    public String sendRequest(String endpointUrl, String soapAction, String bodyXml, Map<String, String> headers) {
        try {
            SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
            SOAPConnection connection = connectionFactory.createConnection();

            MessageFactory messageFactory = MessageFactory.newInstance();
            SOAPMessage soapMessage = messageFactory.createMessage();
            SOAPPart soapPart = soapMessage.getSOAPPart();
            SOAPEnvelope envelope = soapPart.getEnvelope();
            SOAPBody soapBody = envelope.getBody();

            if (bodyXml != null && !bodyXml.isBlank()) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(new InputSource(new java.io.StringReader(bodyXml)));
                soapBody.addDocument(doc);
            }

            if (soapAction != null) {
                soapMessage.getMimeHeaders().addHeader("SOAPAction", soapAction);
            }

            if (headers != null) {
                headers.forEach((k, v) -> soapMessage.getMimeHeaders().addHeader(k, v));
            }

            soapMessage.saveChanges();

            URL endpoint = new URL(endpointUrl);
            SOAPMessage response = connection.call(soapMessage, endpoint);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(response.getSOAPPart()), new StreamResult(writer));

            connection.close();
            return writer.toString();
        } catch (Exception e) {
            log.error("SOAP request failed: {}", endpointUrl, e);
            throw new RuntimeException("SOAP request failed: " + e.getMessage(), e);
        }
    }

    public String sendSecureRequest(String endpointUrl, String soapAction, String bodyXml,
                                     Map<String, String> headers, String keystorePath, String keystorePassword) {
        System.setProperty("javax.net.ssl.keyStore", keystorePath);
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);
        return sendRequest(endpointUrl, soapAction, bodyXml, headers);
    }
}
