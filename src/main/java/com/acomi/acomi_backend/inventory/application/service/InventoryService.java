package com.acomi.acomi_backend.inventory.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.inventory.api.dto.request.CreateInventoryCategoryRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.CreateInventoryItemRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.CreateInventorySupplierRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.InventoryStockMoveRequest;
import com.acomi.acomi_backend.inventory.api.dto.request.UpdateInventoryItemRequest;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryCategoryResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryDashboardResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryItemResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventorySupplierResponse;
import com.acomi.acomi_backend.inventory.api.dto.response.InventoryTransactionResponse;
import com.acomi.acomi_backend.inventory.domain.model.InventoryTxnType;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryCategoryEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventorySupplierEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryTransactionEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryCategoryRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryItemRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventorySupplierRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryAccessService accessService;
    private final InventorySeedService seedService;
    private final SpaceRepository spaceRepository;
    private final InventoryCategoryRepository categoryRepository;
    private final InventoryItemRepository itemRepository;
    private final InventorySupplierRepository supplierRepository;
    private final InventoryTransactionRepository transactionRepository;

    @Transactional
    public List<InventoryCategoryResponse> listCategories(UUID spaceId, UUID callerId) {
        accessService.requireViewInventory(spaceId, callerId);
        ensureSeeded(spaceId);
        return categoryRepository.findBySpaceIdAndIsActiveTrueOrderBySortOrderAscNameAsc(spaceId).stream()
                .map(InventoryCategoryResponse::from)
                .toList();
    }

    @Transactional
    public InventoryCategoryResponse createCategory(
            UUID spaceId, UUID callerId, CreateInventoryCategoryRequest request) {
        accessService.requireManageInventory(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        ensureSeeded(space);
        String name = request.getName().trim();
        String code = toCode(name);
        int sortOrder = categoryRepository
                .findBySpaceIdAndIsActiveTrueOrderBySortOrderAscNameAsc(spaceId)
                .size();
        InventoryCategoryEntity category = categoryRepository.save(InventoryCategoryEntity.builder()
                .space(space)
                .name(name)
                .code(code)
                .iconKey(request.getIconKey() != null && !request.getIconKey().isBlank()
                        ? request.getIconKey().trim()
                        : "Package")
                .sortOrder(sortOrder)
                .isDefault(false)
                .isActive(true)
                .build());
        return InventoryCategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(UUID spaceId, UUID categoryId, UUID callerId) {
        accessService.requireManageInventory(spaceId, callerId);
        InventoryCategoryEntity category = categoryRepository
                .findByIdAndSpaceIdAndIsActiveTrue(categoryId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryCategory", "id", categoryId));
        if (category.isDefault()) {
            throw new BusinessException("Cannot delete system category", HttpStatus.BAD_REQUEST);
        }
        if (itemRepository.existsByCategoryIdAndIsActiveTrue(categoryId)) {
            throw new BusinessException("Category has items", HttpStatus.BAD_REQUEST);
        }
        category.setActive(false);
        categoryRepository.save(category);
    }

    @Transactional
    public List<InventoryItemResponse> listItems(UUID spaceId, UUID callerId, UUID categoryId) {
        accessService.requireViewInventory(spaceId, callerId);
        ensureSeeded(spaceId);
        List<InventoryItemEntity> items = categoryId == null
                ? itemRepository.findActiveBySpaceId(spaceId)
                : itemRepository.findActiveBySpaceIdAndCategoryId(spaceId, categoryId);
        return items.stream().map(InventoryItemResponse::from).toList();
    }

    @Transactional
    public InventoryItemResponse getItem(UUID spaceId, UUID itemId, UUID callerId) {
        accessService.requireViewInventory(spaceId, callerId);
        ensureSeeded(spaceId);
        return InventoryItemResponse.from(loadItem(spaceId, itemId));
    }

    @Transactional
    public InventoryItemResponse createItem(
            UUID spaceId, UUID callerId, CreateInventoryItemRequest request) {
        accessService.requireManageInventory(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        ensureSeeded(space);
        InventoryCategoryEntity category = categoryRepository
                .findByIdAndSpaceIdAndIsActiveTrue(request.getCategoryId(), spaceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "InventoryCategory", "id", request.getCategoryId()));
        InventorySupplierEntity supplier = resolveSupplier(spaceId, request.getSupplierId());

        BigDecimal opening = nullToZero(request.getOpeningStock());
        InventoryItemEntity item = itemRepository.save(InventoryItemEntity.builder()
                .space(space)
                .category(category)
                .name(request.getName().trim())
                .unit(request.getUnit())
                .currentStock(opening)
                .reservedStock(BigDecimal.ZERO)
                .minimumStock(nullToZero(request.getMinimumStock()))
                .location(trimToNull(request.getLocation()))
                .supplier(supplier)
                .purchasePrice(request.getPurchasePrice())
                .averagePrice(request.getPurchasePrice())
                .barcode(trimToNull(request.getBarcode()))
                .notes(trimToNull(request.getNotes()))
                .isDefault(false)
                .isActive(true)
                .build());

        if (opening.signum() > 0) {
            transactionRepository.save(InventoryTransactionEntity.builder()
                    .space(space)
                    .item(item)
                    .itemName(item.getName())
                    .type(InventoryTxnType.STOCK_IN)
                    .quantity(opening)
                    .unit(item.getUnit())
                    .reason("Opening stock")
                    .supplier(supplier)
                    .supplierName(supplier != null ? supplier.getName() : null)
                    .actorName("You")
                    .actorUserId(callerId)
                    .build());
        }
        return InventoryItemResponse.from(loadItem(spaceId, item.getId()));
    }

    @Transactional
    public InventoryItemResponse updateItem(
            UUID spaceId, UUID itemId, UUID callerId, UpdateInventoryItemRequest request) {
        accessService.requireManageInventory(spaceId, callerId);
        InventoryItemEntity item = loadItem(spaceId, itemId);
        if (request.getName() != null && !request.getName().isBlank()) {
            item.setName(request.getName().trim());
        }
        if (request.getCategoryId() != null) {
            InventoryCategoryEntity category = categoryRepository
                    .findByIdAndSpaceIdAndIsActiveTrue(request.getCategoryId(), spaceId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "InventoryCategory", "id", request.getCategoryId()));
            item.setCategory(category);
        }
        if (request.getUnit() != null) {
            item.setUnit(request.getUnit());
        }
        if (request.getMinimumStock() != null) {
            item.setMinimumStock(request.getMinimumStock());
        }
        if (request.getLocation() != null) {
            item.setLocation(trimToNull(request.getLocation()));
        }
        if (request.getSupplierId() != null) {
            item.setSupplier(resolveSupplier(spaceId, request.getSupplierId()));
        }
        if (request.getPurchasePrice() != null) {
            item.setPurchasePrice(request.getPurchasePrice());
        }
        if (request.getAveragePrice() != null) {
            item.setAveragePrice(request.getAveragePrice());
        }
        if (request.getBarcode() != null) {
            item.setBarcode(trimToNull(request.getBarcode()));
        }
        if (request.getNotes() != null) {
            item.setNotes(trimToNull(request.getNotes()));
        }
        if (request.getStatusOverride() != null) {
            item.setStatusOverride(trimToNull(request.getStatusOverride()));
        }
        itemRepository.save(item);
        return InventoryItemResponse.from(loadItem(spaceId, itemId));
    }

    @Transactional
    public void deleteItem(UUID spaceId, UUID itemId, UUID callerId) {
        accessService.requireManageInventory(spaceId, callerId);
        InventoryItemEntity item = loadItem(spaceId, itemId);
        item.setActive(false);
        itemRepository.save(item);
    }

    @Transactional
    public InventoryItemResponse stockMove(
            UUID spaceId, UUID itemId, UUID callerId, InventoryStockMoveRequest request) {
        accessService.requireManageInventory(spaceId, callerId);
        InventoryItemEntity item = loadItem(spaceId, itemId);
        BigDecimal qty = nullToZero(request.getQuantity()).abs();
        BigDecimal current = nullToZero(item.getCurrentStock());

        InventoryTxnType type = request.getType();
        BigDecimal nextStock;
        if (type == InventoryTxnType.ADJUSTMENT && request.getSetAbsoluteStock() != null) {
            nextStock = nullToZero(request.getSetAbsoluteStock());
        } else if (type == InventoryTxnType.STOCK_IN
                || type == InventoryTxnType.PURCHASE
                || (type == InventoryTxnType.ADJUSTMENT
                        && request.getQuantity() != null
                        && request.getQuantity().signum() >= 0)) {
            nextStock = current.add(qty);
        } else {
            nextStock = current.subtract(qty).max(BigDecimal.ZERO);
        }

        if (type == InventoryTxnType.PURCHASE
                && request.getAmount() != null
                && qty.signum() > 0) {
            BigDecimal unitPrice = request.getAmount().divide(qty, 2, RoundingMode.HALF_UP);
            BigDecimal prevAvg = item.getAveragePrice() != null
                    ? item.getAveragePrice()
                    : (item.getPurchasePrice() != null ? item.getPurchasePrice() : unitPrice);
            item.setAveragePrice(prevAvg.add(unitPrice)
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
            item.setPurchasePrice(unitPrice);
        }

        item.setCurrentStock(nextStock);
        itemRepository.save(item);

        InventorySupplierEntity supplier = request.getSupplierId() != null
                ? resolveSupplier(spaceId, request.getSupplierId())
                : item.getSupplier();

        transactionRepository.save(InventoryTransactionEntity.builder()
                .space(item.getSpace())
                .item(item)
                .itemName(item.getName())
                .type(type)
                .quantity(qty)
                .unit(item.getUnit())
                .reason(trimToNull(request.getReason()))
                .reference(trimToNull(request.getReference()))
                .supplier(supplier)
                .supplierName(supplier != null ? supplier.getName() : null)
                .amount(request.getAmount())
                .actorName(request.getActorName() != null && !request.getActorName().isBlank()
                        ? request.getActorName().trim()
                        : "You")
                .actorUserId(callerId)
                .build());

        return InventoryItemResponse.from(loadItem(spaceId, itemId));
    }

    @Transactional
    public List<InventoryTransactionResponse> listTransactions(
            UUID spaceId, UUID callerId, UUID itemId) {
        accessService.requireViewInventory(spaceId, callerId);
        ensureSeeded(spaceId);
        List<InventoryTransactionEntity> list = itemId == null
                ? transactionRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId)
                : transactionRepository.findBySpaceIdAndItemIdOrderByCreatedAtDesc(spaceId, itemId);
        return list.stream().map(InventoryTransactionResponse::from).toList();
    }

    @Transactional
    public List<InventorySupplierResponse> listSuppliers(UUID spaceId, UUID callerId) {
        accessService.requireViewInventory(spaceId, callerId);
        ensureSeeded(spaceId);
        return supplierRepository.findBySpaceIdAndIsActiveTrueOrderByNameAsc(spaceId).stream()
                .map(InventorySupplierResponse::from)
                .toList();
    }

    @Transactional
    public InventorySupplierResponse createSupplier(
            UUID spaceId, UUID callerId, CreateInventorySupplierRequest request) {
        accessService.requireManageInventory(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        ensureSeeded(space);
        InventorySupplierEntity supplier = supplierRepository.save(InventorySupplierEntity.builder()
                .space(space)
                .name(request.getName().trim())
                .phone(trimToNull(request.getPhone()))
                .address(trimToNull(request.getAddress()))
                .notes(trimToNull(request.getNotes()))
                .isActive(true)
                .build());
        return InventorySupplierResponse.from(supplier);
    }

    @Transactional
    public InventoryDashboardResponse getDashboard(UUID spaceId, UUID callerId) {
        accessService.requireViewInventory(spaceId, callerId);
        ensureSeeded(spaceId);
        List<InventoryItemEntity> items = itemRepository.findActiveBySpaceId(spaceId);
        List<InventoryTransactionEntity> txns =
                transactionRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId);

        long lowStockCount = items.stream().filter(this::isLowOrCritical).count();
        long outOfStockCount = items.stream().filter(this::isOutOfStock).count();
        BigDecimal inventoryValue = items.stream()
                .map(item -> {
                    BigDecimal available = nullToZero(item.getCurrentStock())
                            .subtract(nullToZero(item.getReservedStock()))
                            .max(BigDecimal.ZERO);
                    BigDecimal price = item.getAveragePrice() != null
                            ? item.getAveragePrice()
                            : (item.getPurchasePrice() != null
                                    ? item.getPurchasePrice()
                                    : BigDecimal.ZERO);
                    return available.multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);

        List<InventoryTransactionResponse> purchases = txns.stream()
                .filter(t -> t.getType() == InventoryTxnType.PURCHASE)
                .limit(5)
                .map(InventoryTransactionResponse::from)
                .toList();
        List<InventoryTransactionResponse> consumption = txns.stream()
                .filter(t -> t.getType() == InventoryTxnType.CONSUMPTION
                        || t.getType() == InventoryTxnType.STOCK_OUT)
                .limit(5)
                .map(InventoryTransactionResponse::from)
                .toList();
        List<InventoryItemResponse> critical = items.stream()
                .filter(item -> isLowOrCritical(item) || isOutOfStock(item))
                .sorted(Comparator.comparing(InventoryItemEntity::getName))
                .limit(8)
                .map(InventoryItemResponse::from)
                .toList();

        return InventoryDashboardResponse.builder()
                .totalItems(items.size())
                .inventoryValue(inventoryValue)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .supplierCount(supplierRepository.countBySpaceIdAndIsActiveTrue(spaceId))
                .recentPurchases(purchases)
                .recentConsumption(consumption)
                .criticalItems(critical)
                .build();
    }

    private void ensureSeeded(UUID spaceId) {
        ensureSeeded(loadSpace(spaceId));
    }

    private void ensureSeeded(SpaceEntity space) {
        seedService.seedDefaults(space);
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private InventoryItemEntity loadItem(UUID spaceId, UUID itemId) {
        return itemRepository
                .findActiveByIdAndSpaceId(itemId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", "id", itemId));
    }

    private InventorySupplierEntity resolveSupplier(UUID spaceId, UUID supplierId) {
        if (supplierId == null) {
            return null;
        }
        return supplierRepository
                .findByIdAndSpaceIdAndIsActiveTrue(supplierId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("InventorySupplier", "id", supplierId));
    }

    private boolean isOutOfStock(InventoryItemEntity item) {
        return available(item).signum() <= 0;
    }

    private boolean isLowOrCritical(InventoryItemEntity item) {
        BigDecimal available = available(item);
        BigDecimal minimum = nullToZero(item.getMinimumStock());
        if (available.signum() <= 0) {
            return false;
        }
        if (minimum.signum() <= 0) {
            return false;
        }
        return available.compareTo(minimum) <= 0;
    }

    private BigDecimal available(InventoryItemEntity item) {
        return nullToZero(item.getCurrentStock())
                .subtract(nullToZero(item.getReservedStock()))
                .max(BigDecimal.ZERO);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String toCode(String name) {
        String code = name.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (code.isEmpty()) {
            code = "CATEGORY";
        }
        return code.length() > 24 ? code.substring(0, 24) : code;
    }
}
