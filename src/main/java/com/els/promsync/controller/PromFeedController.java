package com.els.promsync.controller;

import com.els.promsync.entity.Product;
import com.els.promsync.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class PromFeedController {

    private final ProductRepository productRepository;

    @GetMapping(value = "/prom.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String generatePromFeed() {
        List<Product> products = productRepository.findAll();

        Map<String, Integer> categoryMap = new HashMap<>();
        int categoryIdCounter = 1;
        for (Product p : products) {
            if (p.getDealerCategory() != null && !categoryMap.containsKey(p.getDealerCategory())) {
                categoryMap.put(p.getDealerCategory(), categoryIdCounter++);
            }
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<!DOCTYPE yml_catalog SYSTEM \"shops.dtd\">\n");
        xml.append("<yml_catalog date=\"").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\">\n");
        xml.append("<shop>\n");

        xml.append("  <name>ELS</name>\n");
        xml.append("  <company>Energy Life Systems</company>\n");
        xml.append("  <url>https://els-energy.com.ua</url>\n");
        xml.append("  <currencies>\n");
        xml.append("    <currency id=\"UAH\" rate=\"1\"/>\n");
        xml.append("  </currencies>\n");

        xml.append("  <categories>\n");
        for (Map.Entry<String, Integer> entry : categoryMap.entrySet()) {
            xml.append("    <category id=\"").append(entry.getValue()).append("\">")
                    .append(escapeXml(entry.getKey()))
                    .append("</category>\n");
        }
        xml.append("  </categories>\n");

        xml.append("  <offers>\n");
        for (Product p : products) {
            if (p.getPriceUah() == null || p.getSku() == null) continue;

            // Наявність. Пром розуміє: available="true" (в наявності), "false" (під замовлення/немає)
            boolean available = "в наявності".equalsIgnoreCase(p.getAvailability());

            xml.append("    <offer id=\"").append(escapeXml(p.getSku())).append("\" available=\"").append(available).append("\">\n");

            // ОБОВ'ЯЗКОВО: Артикул (vendorCode) щоб Пром міг порівнювати товари
            xml.append("      <vendorCode>").append(escapeXml(p.getSku())).append("</vendorCode>\n");

            // Одиниці виміру
            xml.append("      <measure_unit>шт.</measure_unit>\n");

            xml.append("      <price>").append(p.getPriceUah()).append("</price>\n");
            xml.append("      <currencyId>UAH</currencyId>\n");

            if (categoryMap.containsKey(p.getDealerCategory())) {
                xml.append("      <categoryId>").append(categoryMap.get(p.getDealerCategory())).append("</categoryId>\n");
            }

            // 1. Даємо базову назву (щоб Пром пропустив файл) + даємо UA (щоб лягло в укр. версію)
            xml.append("      <name>").append(escapeXml(p.getNameUk())).append("</name>\n");
            xml.append("      <name_ua>").append(escapeXml(p.getNameUk())).append("</name_ua>\n");

            // 2. Те саме з ключовими словами
            if (p.getKeywordsUk() != null && !p.getKeywordsUk().isBlank()) {
                xml.append("      <keywords>").append(escapeXml(p.getKeywordsUk())).append("</keywords>\n");
                xml.append("      <keywords_ua>").append(escapeXml(p.getKeywordsUk())).append("</keywords_ua>\n");
            }

            // Виробник
            if (p.getVendor() != null && !p.getVendor().isBlank()) {
                xml.append("      <vendor>").append(escapeXml(p.getVendor())).append("</vendor>\n");
            }

            // 3. Те саме з описом
            if (p.getDescriptionUk() != null) {
                String cleanDesc = p.getDescriptionUk().replace("\n", "<br/>");
                xml.append("      <description><![CDATA[").append(cleanDesc).append("]]></description>\n");
                xml.append("      <description_ua><![CDATA[").append(cleanDesc).append("]]></description_ua>\n");
            }

            if (p.getTechnicalSpecs() != null) {
                for (Map.Entry<String, String> spec : p.getTechnicalSpecs().entrySet()) {
                    // Фільтруємо пусті значення, щоб не було порожніх тегів
                    if (spec.getValue() != null && !spec.getValue().isBlank()) {
                        xml.append("      <param name=\"").append(escapeXml(spec.getKey())).append("\">")
                                .append(escapeXml(spec.getValue()))
                                .append("</param>\n");
                    }
                }
            }

            if (p.getWarranty() != null && !p.getWarranty().isBlank()) {
                xml.append("      <param name=\"Гарантійний термін (міс)\">").append(escapeXml(p.getWarranty())).append("</param>\n");
            }

            xml.append("    </offer>\n");
        }
        xml.append("  </offers>\n");
        xml.append("</shop>\n");
        xml.append("</yml_catalog>");

        return xml.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}