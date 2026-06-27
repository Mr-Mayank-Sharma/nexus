package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProcurementService {

    private final SupplierRepository supplierRepository;
    private final SupplierContactRepository supplierContactRepository;
    private final SupplierContractRepository supplierContractRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final RfqRepository rfqRepository;
    private final RfqResponseRepository rfqResponseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    public ProcurementService(SupplierRepository supplierRepository,
                              SupplierContactRepository supplierContactRepository,
                              SupplierContractRepository supplierContractRepository,
                              PurchaseRequestRepository purchaseRequestRepository,
                              PurchaseRequestItemRepository purchaseRequestItemRepository,
                              RfqRepository rfqRepository,
                              RfqResponseRepository rfqResponseRepository,
                              PurchaseOrderRepository purchaseOrderRepository,
                              PurchaseOrderItemRepository purchaseOrderItemRepository) {
        this.supplierRepository = supplierRepository;
        this.supplierContactRepository = supplierContactRepository;
        this.supplierContractRepository = supplierContractRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.purchaseRequestItemRepository = purchaseRequestItemRepository;
        this.rfqRepository = rfqRepository;
        this.rfqResponseRepository = rfqResponseRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
    }

    // ==================== SUPPLIERS ====================

    public Page<Supplier> getAllSuppliers(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return supplierRepository.findByTenantId(tenantId, pageable);
    }

    public Supplier getSupplier(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    @Transactional
    public Supplier createSupplier(Supplier s) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        s.setTenantId(tenantId);
        if (s.getSupplierCode() == null || s.getSupplierCode().isBlank()) {
            s.setSupplierCode("SUP-" + System.currentTimeMillis());
        }
        return supplierRepository.save(s);
    }

    @Transactional
    public Supplier updateSupplier(UUID id, Supplier updates) {
        Supplier existing = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));

        for (Field field : Supplier.class.getDeclaredFields()) {
            if ("id".equals(field.getName()) || "tenantId".equals(field.getName())
                    || "createdAt".equals(field.getName()) || "updatedAt".equals(field.getName())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(updates);
                if (value != null) {
                    field.set(existing, value);
                }
            } catch (IllegalAccessException e) {
                throw new BadRequestException("Failed to update field: " + field.getName());
            }
        }

        return supplierRepository.save(existing);
    }

    @Transactional
    public void deleteSupplier(UUID id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        supplierRepository.delete(supplier);
    }

    public List<SupplierContact> getSupplierContacts(UUID supplierId) {
        return supplierContactRepository.findBySupplierId(supplierId);
    }

    @Transactional
    public SupplierContact addSupplierContact(SupplierContact c) {
        return supplierContactRepository.save(c);
    }

    public List<SupplierContract> getSupplierContracts(UUID supplierId) {
        return supplierContractRepository.findBySupplierId(supplierId);
    }

    @Transactional
    public SupplierContract addSupplierContract(SupplierContract c) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        c.setTenantId(tenantId);
        return supplierContractRepository.save(c);
    }

    // ==================== PURCHASE REQUESTS ====================

    public Page<PurchaseRequest> getAllRequests(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return purchaseRequestRepository.findByTenantId(tenantId, pageable);
    }

    public PurchaseRequest getRequest(UUID id) {
        return purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseRequest", id));
    }

    @Transactional
    public PurchaseRequest createRequest(PurchaseRequest req) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        req.setTenantId(tenantId);
        req.setRequestNumber(generatePrNumber());
        return purchaseRequestRepository.save(req);
    }

    @Transactional
    public PurchaseRequest updateRequestStatus(UUID id, String status) {
        PurchaseRequest req = purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseRequest", id));
        req.setStatus(status);
        return purchaseRequestRepository.save(req);
    }

    @Transactional
    public PurchaseRequestItem addRequestItem(PurchaseRequestItem item) {
        return purchaseRequestItemRepository.save(item);
    }

    @Transactional
    public PurchaseRequest submitForApproval(UUID id) {
        PurchaseRequest req = purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseRequest", id));
        req.setStatus("PENDING_APPROVAL");
        return purchaseRequestRepository.save(req);
    }

    @Transactional
    public PurchaseRequest approveRequest(UUID id) {
        PurchaseRequest req = purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseRequest", id));
        req.setStatus("APPROVED");
        req.setApprovedBy(TenantContext.getCurrentTenantId());
        req.setApprovedAt(LocalDateTime.now());
        return purchaseRequestRepository.save(req);
    }

    // ==================== RFQs ====================

    public Page<Rfq> getAllRfqs(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return rfqRepository.findByTenantId(tenantId, pageable);
    }

    public Rfq getRfq(UUID id) {
        return rfqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rfq", id));
    }

    @Transactional
    public Rfq createRfq(Rfq rfq) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        rfq.setTenantId(tenantId);
        rfq.setRfqNumber(generateRfqNumber());
        return rfqRepository.save(rfq);
    }

    @Transactional
    public Rfq submitRfq(UUID id) {
        Rfq rfq = rfqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rfq", id));
        rfq.setStatus("SUBMITTED");
        return rfqRepository.save(rfq);
    }

    public List<RfqResponse> getRfqResponses(UUID rfqId) {
        return rfqResponseRepository.findByRfqId(rfqId);
    }

    @Transactional
    public RfqResponse addRfqResponse(RfqResponse resp) {
        return rfqResponseRepository.save(resp);
    }

    // ==================== PURCHASE ORDERS ====================

    public Page<PurchaseOrder> getAllPurchaseOrders(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return purchaseOrderRepository.findByTenantId(tenantId, pageable);
    }

    public PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(PurchaseOrder po) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        po.setTenantId(tenantId);
        po.setPoNumber(generatePoNumber(tenantId));
        po.setOrderDate(LocalDate.now());
        po = purchaseOrderRepository.save(po);

        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPoId(po.getId());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items) {
            if (item.getTotalPrice() != null) {
                subtotal = subtotal.add(item.getTotalPrice());
            }
        }
        po.setSubtotal(subtotal);
        po.setTotalAmount(subtotal.add(po.getTaxAmount() != null ? po.getTaxAmount() : BigDecimal.ZERO)
                .add(po.getShippingCost() != null ? po.getShippingCost() : BigDecimal.ZERO));

        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder updatePurchaseOrderStatus(UUID id, String status) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
        po.setStatus(status);
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder receiveItems(UUID poId, List<Map<String, Object>> receivedItems) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));

        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPoId(poId);

        for (Map<String, Object> received : receivedItems) {
            String sku = (String) received.get("sku");
            Integer qty = received.get("quantityReceived") instanceof Integer
                    ? (Integer) received.get("quantityReceived")
                    : ((Number) received.get("quantityReceived")).intValue();

            PurchaseOrderItem item = items.stream()
                    .filter(i -> i.getSku().equals(sku))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Item with SKU " + sku + " not found on PO"));

            item.setQuantityReceived(item.getQuantityReceived() + qty);
            purchaseOrderItemRepository.save(item);
        }

        items = purchaseOrderItemRepository.findByPoId(poId);
        boolean allReceived = items.stream()
                .allMatch(i -> i.getQuantityReceived() >= i.getQuantityOrdered());
        po.setIsFullyReceived(allReceived);

        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder approvePurchaseOrder(UUID id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
        po.setStatus("APPROVED");
        po.setApprovedBy(TenantContext.getCurrentTenantId());
        po.setApprovedAt(LocalDateTime.now());
        return purchaseOrderRepository.save(po);
    }

    // ==================== HELPERS ====================

    private String generatePoNumber(UUID tenantId) {
        String prefix = tenantId.toString().substring(0, 8).toUpperCase();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PO-" + prefix + "-" + ts;
    }

    private String generatePrNumber() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PR-" + ts;
    }

    private String generateRfqNumber() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "RFQ-" + ts;
    }
}
