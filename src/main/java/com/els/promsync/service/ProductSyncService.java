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

    @Value("${app.exchange-rate:44}")
    private BigDecimal exchangeRate;

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

        BigDecimal basePriceUsd = BigDecimal.ZERO;
        if (row.size() > 4 && row.get(4) != null) {
            try {
                String priceStr = row.get(4).toString().replaceAll("[^0-9.]", "");
                basePriceUsd = new BigDecimal(priceStr);
            } catch (Exception e) {
                log.warn("Не вдалося розпарсити ціну: {}", row.get(4));
            }
        }

        // Рахуємо фінальну ціну в гривнях з націнкою
        BigDecimal priceUah = calculateFinalPrice(basePriceUsd);

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
        if (product.getId() == null) {
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

    private BigDecimal calculateFinalPrice(BigDecimal priceUsd) {
        if (priceUsd == null || priceUsd.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal markupPercentage;
        double price = priceUsd.doubleValue();
        if (price < 1000) {
            markupPercentage = new BigDecimal("1.15");
        } else if (price <= 2500) {
            markupPercentage = new BigDecimal("1.12");
        } else {
            markupPercentage = new BigDecimal("1.10");
        }
        return priceUsd.multiply(markupPercentage).multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
    }
}
