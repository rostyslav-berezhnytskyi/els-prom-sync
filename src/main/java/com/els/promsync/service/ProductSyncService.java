package com.els.promsync.service;

import com.els.promsync.dto.AiProductResponse;
import com.els.promsync.entity.Product;
import com.els.promsync.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncService {

    private final ProductRepository productRepository;
    private final OpenAiService openAiService; // Наш сервіс для GPT
    private final PriceCalculationService priceCalculationService;
    private final ProductCategoryResolverService productCategoryResolverService;

    @Value("${app.ai-enabled:true}")
    private boolean aiEnabled;

    @Transactional
    public void processRow(List<Object> row, String category) {
        if (row == null || row.isEmpty() || row.get(0).toString().trim().isEmpty()) {
            return;
        }

        String sku = row.get(0).toString().trim();
        String originalName = row.size() > 1 && row.get(1) != null ? row.get(1).toString().trim() : "";
        String availability = parseAvailability(row, category);
        String effectiveCategory = productCategoryResolverService.resolve(
                category,
                null,
                sku,
                originalName
        );

        System.out.println(
                "CATEGORY RESOLVED | SOURCE: " + category +
                        " | EFFECTIVE: " + effectiveCategory +
                        " | SKU: " + sku
        );

        // ГАРАНТІЯ
        String warranty = parseWarranty(row, category);

        BigDecimal basePriceUsd = parseDealerPrice(row, category);

        // Рахуємо фінальну ціну в гривнях з націнкою
        BigDecimal priceUah = priceCalculationService.calculateFinalPrice(
                basePriceUsd,
                originalName,
                effectiveCategory
        );

        // Шукаємо в базі або створюємо новий
        Product product = productRepository.findBySku(sku).orElse(new Product());

        product.setSku(sku);
        product.setOriginalName(originalName);
        product.setDealerCategory(effectiveCategory);
        product.setAvailability(availability);
        product.setBasePriceUsd(basePriceUsd);
        product.setPriceUah(priceUah);
        product.setWarranty(warranty);

        // Якщо товар новий, підключаємо магію AI
        if (product.getId() == null && aiEnabled) {
            log.info("Новий товар [{}]. Категорія: [{}]. Запитуємо AI...", sku, effectiveCategory);

            // Викликаємо оновлений метод
            AiProductResponse aiData = openAiService.enrichProduct(originalName, effectiveCategory);

            if (aiData != null) {
                // Українська
                product.setNameUk(aiData.seoNameUa());
                product.setDescriptionUk(aiData.seoDescriptionUa());
                if (aiData.keywordsUa() != null) product.setKeywordsUk(String.join(", ", aiData.keywordsUa()));

                // Російська
                product.setNameRu(aiData.seoNameRu());
                product.setDescriptionRu(aiData.seoDescriptionRu());
                if (aiData.keywordsRu() != null) product.setKeywordsRu(String.join(", ", aiData.keywordsRu()));

                product.setTechnicalSpecs(aiData.specs());
                product.setVendor(aiData.vendor());
            }
        }

        productRepository.save(product);
    }

    /**
     * Safely reads a cell value from Google Sheets row.
     */
    private String getCell(List<Object> row, int index) {
        if (row == null || row.size() <= index || row.get(index) == null) {
            return "";
        }

        return row.get(index).toString().trim();
    }

    /**
     * Parses USD price from Google Sheets.
     *
     * Examples:
     * "715" -> 715
     * "0,165" -> 0.165
     * "0,205*" -> 0.205
     * "-" -> 0
     */
    private BigDecimal parseUsdPrice(String rawValue) {
        if (rawValue == null) {
            return BigDecimal.ZERO;
        }

        String value = rawValue
                .trim()
                .replace(",", ".")
                .replaceAll("[^0-9.]", "");

        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse USD price from value: {}", rawValue);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Reads product availability from Google Sheets.
     *
     * Most tabs use column C.
     * BESS tab uses column E.
     */
    private String parseAvailability(List<Object> row, String sourceCategory) {
        if (isBess(sourceCategory)) {
            return getCell(row, 4);
        }

        return getCell(row, 2);
    }

    /**
     * Prints product price calculation preview without saving anything to database.
     * Used only for testing Google Sheets parsing and price calculation.
     */
    public void previewRowPrice(List<Object> row, String category, int sheetRowNumber) {
        String sku = getCell(row, 0);
        String originalName = getCell(row, 1);
        String rawDealerPrice = getCell(row, 4);

        BigDecimal dealerPriceUsd = parseUsdPrice(rawDealerPrice);

        BigDecimal finalPriceUah = priceCalculationService.calculateFinalPrice(
                dealerPriceUsd,
                originalName,
                category
        );

        System.out.println(
                "PRICE TEST | ROW: " + sheetRowNumber +
                        " | TAB: " + category +
                        " | SKU: " + sku +
                        " | DEALER PRICE: " + dealerPriceUsd +
                        " | FINAL PRICE UAH: " + finalPriceUah +
                        " | NAME: " + originalName
        );
    }

    /**
     * Reads dealer price from Google Sheets.
     *
     * Most tabs use column E: price without VAT.
     * Solar cable uses column D if column E is empty.
     * BESS uses column F because this tab has a different structure.
     */
    private BigDecimal parseDealerPrice(List<Object> row, String sourceCategory) {
        if (isBess(sourceCategory)) {
            return parseUsdPrice(getCell(row, 5));
        }

        String priceWithoutVat = getCell(row, 4);

        if (hasPrice(priceWithoutVat)) {
            return parseUsdPrice(priceWithoutVat);
        }

        if (isSolarCable(sourceCategory)) {
            return parseUsdPrice(getCell(row, 3));
        }

        return BigDecimal.ZERO;
    }

    private boolean isBess(String category) {
        if (category == null) {
            return false;
        }

        return category.toLowerCase().contains("bess");
    }

    private boolean isSolarCable(String category) {
        if (category == null) {
            return false;
        }

        return category.toLowerCase().contains("сонячний кабель");
    }

    private boolean hasPrice(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String cleaned = value.trim();

        return !cleaned.equals("-") && cleaned.matches(".*\\d.*");
    }

    /**
     * Reads warranty from Google Sheets.
     *
     * Most tabs use column F for warranty.
     * BESS tab has price in column F, so warranty is not available there.
     */
    private String parseWarranty(List<Object> row, String sourceCategory) {
        if (isBess(sourceCategory)) {
            return "";
        }

        String rawWarranty = getCell(row, 5);

        if (rawWarranty.isBlank()) {
            return "";
        }

        String yearsStr = rawWarranty
                .split("\\+")[0]
                .replaceAll("[^0-9]", "");

        if (yearsStr.isBlank()) {
            return "";
        }

        int months = Integer.parseInt(yearsStr) * 12;
        return String.valueOf(months);
    }
}
