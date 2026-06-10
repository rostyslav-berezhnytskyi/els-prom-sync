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

    @Value("${app.ai-enabled:true}")
    private boolean aiEnabled;

    @Transactional
    public void processRow(List<Object> row, String category) {
        if (row == null || row.isEmpty() || row.get(0).toString().trim().isEmpty()) {
            return;
        }

        String sku = row.get(0).toString().trim();
        String originalName = row.size() > 1 && row.get(1) != null ? row.get(1).toString().trim() : "";
        String availability = row.size() > 2 && row.get(2) != null ? row.get(2).toString().trim() : "";

        // ГАРАНТІЯ
        String warranty = "";
        if (row.size() > 5 && row.get(5) != null) {
            String rawWarranty = row.get(5).toString().trim();
            String yearsStr = rawWarranty.split("\\+")[0].replaceAll("[^0-9]", "");
            if (!yearsStr.isEmpty()) {
                int months = Integer.parseInt(yearsStr) * 12;
                warranty = String.valueOf(months);
            }
        }

        BigDecimal basePriceUsd = parseUsdPrice(getCell(row, 4));

        // Рахуємо фінальну ціну в гривнях з націнкою
        BigDecimal priceUah = priceCalculationService.calculateFinalPrice(
                basePriceUsd,
                originalName,
                category
        );

        // Шукаємо в базі або створюємо новий
        Product product = productRepository.findBySku(sku).orElse(new Product());

        product.setSku(sku);
        product.setOriginalName(originalName);
        product.setDealerCategory(category);
        product.setAvailability(availability);
        product.setBasePriceUsd(basePriceUsd);
        product.setPriceUah(priceUah);
        product.setWarranty(warranty);

        // Якщо товар новий, підключаємо магію AI
        if (product.getId() == null && aiEnabled) {
            log.info("Новий товар [{}]. Категорія: [{}]. Запитуємо AI...", sku, category);

            // Викликаємо оновлений метод
            AiProductResponse aiData = openAiService.enrichProduct(originalName, category);

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
}
