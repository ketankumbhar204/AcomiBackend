package com.acomi.acomi_backend.inventory.application.catalog;

import com.acomi.acomi_backend.inventory.domain.model.InventoryProfileKind;
import com.acomi.acomi_backend.inventory.domain.model.InventoryUnit;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Configuration-only inventory profiles. Persisted rows are created by {@code InventorySeedService}.
 */
public final class InventoryProfiles {

    private InventoryProfiles() {}

    public record SeedCategory(String code, String name, String iconKey) {}

    public record SeedItem(
            String name,
            String categoryCode,
            InventoryUnit unit,
            BigDecimal currentStock,
            BigDecimal minimumStock,
            String location,
            BigDecimal purchasePrice) {}

    public record Profile(
            InventoryProfileKind kind,
            String name,
            boolean supportsExpiry,
            boolean supportsSupplier,
            boolean supportsWarranty,
            boolean supportsAssetAssignment,
            String defaultSupplierName,
            List<SeedCategory> categories,
            List<SeedItem> items) {}

    public static Profile forSpaceType(SpaceType type) {
        return switch (type) {
            case MESS -> FOOD;
            case RENTAL -> FURNITURE;
            case PG, HOSTEL, CO_LIVING -> ASSET;
        };
    }

    private static final Profile FOOD = new Profile(
            InventoryProfileKind.FOOD,
            "Food Inventory",
            true,
            true,
            false,
            false,
            "Local Kirana",
            List.of(
                    new SeedCategory("GRAINS", "Grains", "Wheat"),
                    new SeedCategory("DAIRY", "Dairy", "Milk"),
                    new SeedCategory("VEGETABLES", "Vegetables", "Apple"),
                    new SeedCategory("OIL", "Oil", "Droplets"),
                    new SeedCategory("SPICES", "Spices", "Package")),
            List.of(
                    item("Rice", "GRAINS", InventoryUnit.KG, "20", "Dry store", "55"),
                    item("Wheat Flour", "GRAINS", InventoryUnit.KG, "15", "Dry store", "42"),
                    item("Semolina", "GRAINS", InventoryUnit.KG, "5", "Dry store", "60"),
                    item("Milk", "DAIRY", InventoryUnit.LITRE, "10", "Fridge", "56"),
                    item("Curd", "DAIRY", InventoryUnit.KG, "5", "Fridge", "50"),
                    item("Butter", "DAIRY", InventoryUnit.KG, "2", "Fridge", "520"),
                    item("Onion", "VEGETABLES", InventoryUnit.KG, "8", "Kitchen", "30"),
                    item("Tomato", "VEGETABLES", InventoryUnit.KG, "5", "Kitchen", "40"),
                    item("Potato", "VEGETABLES", InventoryUnit.KG, "10", "Kitchen", "25"),
                    item("Sunflower Oil", "OIL", InventoryUnit.LITRE, "8", "Kitchen", "160"),
                    item("Salt", "SPICES", InventoryUnit.KG, "2", "Kitchen", "20"),
                    item("Turmeric", "SPICES", InventoryUnit.KG, "0.5", "Kitchen", "280"),
                    item("Chili Powder", "SPICES", InventoryUnit.KG, "0.5", "Kitchen", "320")));

    private static final Profile ASSET = new Profile(
            InventoryProfileKind.ASSET,
            "Asset Inventory",
            false,
            true,
            true,
            true,
            "General Supplies",
            List.of(
                    new SeedCategory("FURNITURE", "Furniture", "Sofa"),
                    new SeedCategory("BEDDING", "Bedding", "BedDouble"),
                    new SeedCategory("CLEANING", "Cleaning", "Sparkles"),
                    new SeedCategory("ELECTRICAL", "Electrical", "Fan")),
            List.of(
                    item("Chair", "FURNITURE", InventoryUnit.PIECE, "4", "Store room", "900"),
                    item("Table", "FURNITURE", InventoryUnit.PIECE, "2", "Store room", "2500"),
                    item("Cot", "FURNITURE", InventoryUnit.PIECE, "3", "Store room", "4500"),
                    item("Mattress", "BEDDING", InventoryUnit.PIECE, "3", "Linen cupboard", "3500"),
                    item("Pillow", "BEDDING", InventoryUnit.PIECE, "10", "Linen cupboard", "250"),
                    item("Bedsheet", "BEDDING", InventoryUnit.PIECE, "12", "Linen cupboard", "350"),
                    item("Mop", "CLEANING", InventoryUnit.PIECE, "2", "Utility", "220"),
                    item("Bucket", "CLEANING", InventoryUnit.PIECE, "4", "Utility", "180"),
                    item("Cleaning Liquid", "CLEANING", InventoryUnit.LITRE, "3", "Utility", "90"),
                    item("Fan", "ELECTRICAL", InventoryUnit.PIECE, "1", "Store room", "2200"),
                    item("Tube Light", "ELECTRICAL", InventoryUnit.PIECE, "5", "Store room", "180")));

    private static final Profile FURNITURE = new Profile(
            InventoryProfileKind.FURNITURE,
            "Furniture & Appliances",
            false,
            true,
            true,
            true,
            "Home Appliances Vendor",
            List.of(
                    new SeedCategory("FURNITURE", "Furniture", "Sofa"),
                    new SeedCategory("APPLIANCES", "Appliances", "Refrigerator"),
                    new SeedCategory("LAUNDRY", "Laundry", "WashingMachine"),
                    new SeedCategory("SOFT", "Soft Furnishings", "Shirt"),
                    new SeedCategory("KEYS", "Keys", "KeyRound")),
            List.of(
                    item("Sofa Set", "FURNITURE", InventoryUnit.SET, "0", "Unit A — Living", "28000"),
                    item("Dining Table", "FURNITURE", InventoryUnit.SET, "0", "Unit A — Dining", "12000"),
                    item("Split AC 1.5T", "APPLIANCES", InventoryUnit.PIECE, "0", "Unit A — Bedroom", "38000"),
                    item("Refrigerator 190L", "APPLIANCES", InventoryUnit.PIECE, "0", "Unit A — Kitchen", "18000"),
                    item("Microwave", "APPLIANCES", InventoryUnit.PIECE, "1", "Store", "7500"),
                    item("Washing Machine", "LAUNDRY", InventoryUnit.PIECE, "0", "Unit A — Utility", "22000"),
                    item("Curtains (Living)", "SOFT", InventoryUnit.SET, "1", "Unit A", "2400"),
                    item("Unit Keys", "KEYS", InventoryUnit.SET, "2", "Office", "120")));

    /** Seeded stock always starts at 0 — owners stock in after setup. */
    private static SeedItem item(
            String name,
            String categoryCode,
            InventoryUnit unit,
            String minimum,
            String location,
            String price) {
        return new SeedItem(
                name,
                categoryCode,
                unit,
                BigDecimal.ZERO,
                new BigDecimal(minimum),
                location,
                new BigDecimal(price));
    }
}
