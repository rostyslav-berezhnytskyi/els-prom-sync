package com.els.promsync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceCalculationService {

    private static final Pattern PANEL_POWER_WATTS_PATTERN =
            Pattern.compile("(\\d{3,4})\\s*(Вт|W)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PANEL_POWER_MODEL_PATTERN =
            Pattern.compile("(?<!\\d)([3-9]\\d{2})\\s*M\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);


    @Value("${pricing.panel-markup:1.15}")
    private BigDecimal panelMarkup;

    @Value("${pricing.cable-markup:1.15}")
    private BigDecimal cableMarkup;

    @Value("${pricing.cheap-price-usd:200}")
    private BigDecimal cheapPriceUsd;

    @Value("${pricing.standard-price-usd:600}")
    private BigDecimal standardPriceUsd;

    @Value("${pricing.high-price-usd:2000}")
    private BigDecimal highPriceUsd;

    @Value("${pricing.max-price-usd:6000}")
    private BigDecimal maxPriceUsd;

    @Value("${pricing.cheap-markup:1.35}")
    private BigDecimal cheapMarkup;

    @Value("${pricing.standard-markup:1.15}")
    private BigDecimal standardMarkup;

    @Value("${pricing.min-markup:1.08}")
    private BigDecimal minMarkup;

    private final CurrencyRateService currencyRateService;

    /**
     * Calculates final product price in UAH.
     *
     * Solar panels use dealer price as USD per watt.
     * Other products use dealer price as USD per item.
     */
    public BigDecimal calculateFinalPrice(BigDecimal dealerPriceUsd, String productName, String category) {
        if (dealerPriceUsd == null || dealerPriceUsd.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (isSolarPanel(category)) {
            return calculatePanelPrice(dealerPriceUsd, productName);
        }

        if (isSolarCable(category)) {
            return calculateFixedMarkupProductPrice(dealerPriceUsd, cableMarkup);
        }

        return calculateRegularProductPrice(dealerPriceUsd);
    }

    /**
     * Calculates solar panel price.
     *
     * Example:
     * 0.165 USD/W * 610 W * 44 UAH/USD * 1.15 = final UAH price.
     */
    private BigDecimal calculatePanelPrice(BigDecimal pricePerWattUsd, String productName) {
        Integer powerWatts = extractPanelPowerWatts(productName);

        if (powerWatts == null) {
            log.warn("Cannot calculate panel price. Power not found in product name: {}", productName);
            return BigDecimal.ZERO;
        }

        BigDecimal itemCostUsd = pricePerWattUsd.multiply(BigDecimal.valueOf(powerWatts));
        BigDecimal exchangeRate = currencyRateService.getUsdSellRate();

        return itemCostUsd
                .multiply(exchangeRate)
                .multiply(panelMarkup)
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Calculates price for inverters, batteries and other regular products.
     */
    private BigDecimal calculateRegularProductPrice(BigDecimal itemCostUsd) {
        BigDecimal markup = calculateFloatingMarkup(itemCostUsd);
        BigDecimal exchangeRate = currencyRateService.getUsdSellRate();

        return itemCostUsd
                .multiply(exchangeRate)
                .multiply(markup)
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Checks whether product category is solar panels.
     */
    private boolean isSolarPanel(String category) {
        if (category == null) {
            return false;
        }

        String value = category.toLowerCase();

        return value.contains("фотоелектр")
                || value.contains("панел")
                || value.contains("модул");
    }

    /**
     * Extracts panel power from product name.
     *
     * Examples:
     * "610Вт" -> 610
     * "505W" -> 505
     * "715M" -> 715
     */
    private Integer extractPanelPowerWatts(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }

        Matcher wattsMatcher = PANEL_POWER_WATTS_PATTERN.matcher(productName);
        if (wattsMatcher.find()) {
            return Integer.parseInt(wattsMatcher.group(1));
        }

        Matcher modelMatcher = PANEL_POWER_MODEL_PATTERN.matcher(productName);
        if (modelMatcher.find()) {
            return Integer.parseInt(modelMatcher.group(1));
        }

        return null;
    }

    /**
     * Smoothly changes markup between two price points.
     */
    private BigDecimal interpolateMarkup(
            BigDecimal priceUsd,
            BigDecimal fromPriceUsd,
            BigDecimal toPriceUsd,
            BigDecimal fromMarkup,
            BigDecimal toMarkup
    ) {
        BigDecimal priceRange = toPriceUsd.subtract(fromPriceUsd);
        BigDecimal markupRange = fromMarkup.subtract(toMarkup);
        BigDecimal currentPosition = priceUsd.subtract(fromPriceUsd);

        BigDecimal decrease = currentPosition
                .multiply(markupRange)
                .divide(priceRange, 4, RoundingMode.HALF_UP);

        return fromMarkup.subtract(decrease);
    }

    /**
     * Calculates floating markup for regular products.
     *
     * Logic:
     * - very cheap products have high markup
     * - common products have stable 15% markup
     * - expensive products gradually go down to minimum markup
     */
    private BigDecimal calculateFloatingMarkup(BigDecimal priceUsd) {
        if (priceUsd.compareTo(cheapPriceUsd) <= 0) {
            return cheapMarkup;
        }

        if (priceUsd.compareTo(standardPriceUsd) < 0) {
            return interpolateMarkup(
                    priceUsd,
                    cheapPriceUsd,
                    standardPriceUsd,
                    cheapMarkup,
                    standardMarkup
            );
        }

        if (priceUsd.compareTo(highPriceUsd) <= 0) {
            return standardMarkup;
        }

        if (priceUsd.compareTo(maxPriceUsd) < 0) {
            return interpolateMarkup(
                    priceUsd,
                    highPriceUsd,
                    maxPriceUsd,
                    standardMarkup,
                    minMarkup
            );
        }

        return minMarkup;
    }

    private BigDecimal calculateFixedMarkupProductPrice(BigDecimal itemCostUsd, BigDecimal markup) {
        BigDecimal exchangeRate = currencyRateService.getUsdSellRate();

        return itemCostUsd
                .multiply(exchangeRate)
                .multiply(markup)
                .setScale(0, RoundingMode.HALF_UP);
    }

    private boolean isSolarCable(String category) {
        if (category == null) {
            return false;
        }

        return category.toLowerCase().contains("сонячний кабель");
    }
}