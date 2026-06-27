package com.els.promsync.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PromPriceCalculationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    @Value("${prom.price-rounding-step-uah:10}")
    private BigDecimal roundingStepUah;

    @Value("${prom.commission-percent.solar-panels:0}")
    private BigDecimal solarPanelsCommission;

    @Value("${prom.commission-percent.battery-lv:0}")
    private BigDecimal batteryLvCommission;

    @Value("${prom.commission-percent.battery-hv:0}")
    private BigDecimal batteryHvCommission;

    @Value("${prom.commission-percent.hybrid-inverters:0}")
    private BigDecimal hybridInvertersCommission;

    @Value("${prom.commission-percent.grid-inverters:0}")
    private BigDecimal gridInvertersCommission;

    @Value("${prom.commission-percent.autonomous-inverters:0}")
    private BigDecimal autonomousInvertersCommission;

    @Value("${prom.commission-percent.solar-cable:0}")
    private BigDecimal solarCableCommission;

    @Value("${prom.commission-percent.bess:0}")
    private BigDecimal bessCommission;

    @Value("${prom.commission-percent.inverter-meters:0}")
    private BigDecimal inverterMetersCommission;

    @Value("${prom.commission-percent.battery-bms:0}")
    private BigDecimal batteryBmsCommission;

    @Value("${prom.commission-percent.battery-racks:0}")
    private BigDecimal batteryRacksCommission;

    @Value("${prom.commission-percent.bess-accessories:0}")
    private BigDecimal bessAccessoriesCommission;

    @Value("${prom.commission-percent.uncategorized:0}")
    private BigDecimal uncategorizedCommission;

    @Value("${prom.commission-threshold-uah:10000}")
    private BigDecimal commissionThresholdUah;

    @Value("${prom.reduced-commission-percent:2}")
    private BigDecimal reducedCommissionPercent;

    /**
     * Adds Prom commission to already calculated internal price.
     *
     * If Prom commission is 8.59%, then:
     * finalPromPrice = internalPrice / (1 - 0.0859)
     */
    public BigDecimal calculatePromPrice(BigDecimal internalPriceUah, String category) {
        if (internalPriceUah == null || internalPriceUah.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal commissionPercent = resolveCommissionPercent(category);

        BigDecimal priceWithCommission = applyCommission(internalPriceUah, commissionPercent);

        return roundUpToStep(priceWithCommission, roundingStepUah);
    }

    private BigDecimal applyCommission(BigDecimal internalPrice, BigDecimal categoryCommissionPercent) {
        if (categoryCommissionPercent == null || categoryCommissionPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return internalPrice;
        }

        BigDecimal categoryRate = categoryCommissionPercent.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal reducedRate = reducedCommissionPercent.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);

        if (categoryRate.compareTo(BigDecimal.ONE) >= 0 || reducedRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Prom commission percent must be less than 100");
        }

        BigDecimal netAtThreshold = commissionThresholdUah.multiply(BigDecimal.ONE.subtract(categoryRate));

        if (internalPrice.compareTo(netAtThreshold) <= 0) {
            return internalPrice.divide(BigDecimal.ONE.subtract(categoryRate), 2, RoundingMode.HALF_UP);
        }

        BigDecimal thresholdCommissionDifference = commissionThresholdUah.multiply(categoryRate.subtract(reducedRate));

        return internalPrice
                .add(thresholdCommissionDifference)
                .divide(BigDecimal.ONE.subtract(reducedRate), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal roundUpToStep(BigDecimal price, BigDecimal step) {
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0) {
            return price.setScale(0, RoundingMode.HALF_UP);
        }

        return price
                .divide(step, 0, RoundingMode.UP)
                .multiply(step)
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveCommissionPercent(String category) {
        String key = resolveCategoryKey(category);

        return switch (key) {
            case "solar-panels" -> solarPanelsCommission;
            case "battery-lv" -> batteryLvCommission;
            case "battery-hv" -> batteryHvCommission;
            case "hybrid-inverters" -> hybridInvertersCommission;
            case "grid-inverters" -> gridInvertersCommission;
            case "autonomous-inverters" -> autonomousInvertersCommission;
            case "solar-cable" -> solarCableCommission;
            case "bess" -> bessCommission;
            case "inverter-meters" -> inverterMetersCommission;
            case "battery-bms" -> batteryBmsCommission;
            case "battery-racks" -> batteryRacksCommission;
            case "bess-accessories" -> bessAccessoriesCommission;
            default -> uncategorizedCommission;
        };
    }

    private String resolveCategoryKey(String category) {
        if (category == null || category.isBlank()) {
            return "uncategorized";
        }

        String value = category.trim().toLowerCase();

        return switch (value) {
            case "фотоелектричні модулі" -> "solar-panels";
            case "акумулятори lv" -> "battery-lv";
            case "акумулятори hv" -> "battery-hv";
            case "гібридні інвертори" -> "hybrid-inverters";
            case "мережеві інвертори" -> "grid-inverters";
            case "автономні інвертори" -> "autonomous-inverters";
            case "сонячний кабель" -> "solar-cable";
            case "bess" -> "bess";
            case "лічильники до інверторів" -> "inverter-meters";
            case "bms для акумуляторів" -> "battery-bms";
            case "стійки для акумуляторів" -> "battery-racks";
            case "комплектуючі bess" -> "bess-accessories";
            default -> "uncategorized";
        };
    }
}
