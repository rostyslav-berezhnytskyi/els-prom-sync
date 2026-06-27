package com.els.promsync.dto;

public enum AvailabilityStatus {

    IN_STOCK,
    ON_ORDER,
    RESERVED,
    OUT_OF_STOCK,
    UNKNOWN;

    public static AvailabilityStatus fromDealerText(String availability) {
        if (availability == null || availability.isBlank()) {
            return UNKNOWN;
        }

        String value = availability.trim().toLowerCase();

        if (value.equals("-")
                || value.equals("—")
                || value.contains("відсут")
                || value.contains("немає")
                || value.contains("нема")
                || value.contains("нет в наличии")
                || value.contains("закінч")) {
            return OUT_OF_STOCK;
        }

        if (value.contains("резерв")) {
            return RESERVED;
        }

        if (value.contains("в дорозі")
                || value.contains("в дороге")
                || value.contains("під замовлення")
                || value.contains("под заказ")
                || value.contains("очікується")
                || value.contains("ожидается")) {
            return ON_ORDER;
        }

        if (value.contains("в наявності")
                || value.contains("на складі")
                || value.contains("в наличии")
                || value.contains("на складе")) {
            return IN_STOCK;
        }

        return UNKNOWN;
    }

    public boolean isAvailableForProm() {
        return this == IN_STOCK;
    }

    public boolean isReadyToShip() {
        return this == IN_STOCK;
    }

    public boolean isPreorderOrOnTheWay() {
        return this == ON_ORDER;
    }

    public boolean isReserved() {
        return this == RESERVED;
    }

    public boolean isUnavailableOrUnknown() {
        return this == OUT_OF_STOCK || this == UNKNOWN;
    }
}