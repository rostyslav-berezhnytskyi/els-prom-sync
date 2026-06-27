package com.els.promsync.controller;

import com.els.promsync.entity.Product;
import com.els.promsync.repository.ProductRepository;
import com.els.promsync.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.els.promsync.util.XmlSafe;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class PromFeedController {

    private final ProductRepository productRepository;
    private final ProductImageService productImageService;

    @GetMapping(value = "/prom.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String generatePromFeed() {
        List<Product> products = productRepository.findAll();

        Map<String, Long> categoryMap = new HashMap<>();
        long categoryIdCounter = 900000000L;

        for (Product p : products) {
            if (Boolean.FALSE.equals(p.getActiveFromDealer())) {
                continue;}

            String category = p.getDealerCategory();

            if (category == null || category.isBlank() || categoryMap.containsKey(category)) {
                continue;
            }

            Long fixedCategoryId = getFeedCategoryId(category);

            if (fixedCategoryId != null) {
                categoryMap.put(category, fixedCategoryId);
            } else {
                categoryMap.put(category, categoryIdCounter++);
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
        for (Map.Entry<String, Long> entry : categoryMap.entrySet()) {
            xml.append("    <category id=\"").append(entry.getValue()).append("\">")
                    .append(XmlSafe.text(entry.getKey()))
                    .append("</category>\n");
        }
        xml.append("  </categories>\n");

        xml.append("  <offers>\n");
        for (Product p : products) {
            if (Boolean.FALSE.equals(p.getActiveFromDealer())) {
                continue;}

            if (p.getPriceUah() == null || p.getSku() == null) continue;

            String availability = p.getAvailability() == null
                    ? ""
                    : p.getAvailability().trim().toLowerCase();

            String promAvailability = mapAvailabilityForYml(availability);
            boolean readyToShip = isReadyToShip(availability);

            xml.append("    <offer id=\"")
                    .append(XmlSafe.attribute(p.getSku()))
                    .append("\" available=\"")
                    .append(promAvailability)
                    .append("\"");

            if (readyToShip) {
                xml.append(" in_stock=\"true\"");
            }

            xml.append(">\n");

            // ОБОВ'ЯЗКОВО: Артикул (vendorCode) щоб Пром міг порівнювати товари
            xml.append("      <vendorCode>").append(XmlSafe.text(p.getSku())).append("</vendorCode>\n");

            // Одиниці виміру
            xml.append("      <measure_unit>шт.</measure_unit>\n");

            xml.append("      <price>").append(p.getPriceUah()).append("</price>\n");
            xml.append("      <currencyId>UAH</currencyId>\n");

            if (categoryMap.containsKey(p.getDealerCategory())) {
                xml.append("      <categoryId>").append(categoryMap.get(p.getDealerCategory())).append("</categoryId>\n");
            }

            for (String imageUrl : productImageService.findImageUrls(p)) {
                xml.append("      <picture>")
                        .append(XmlSafe.text(imageUrl))
                        .append("</picture>\n");
            }

            if (isReadyToShip(availability)) {
                xml.append("      <regions>\n");
                xml.append("        <region>Київ</region>\n");
                xml.append("      </regions>\n");
            }

            if (isPreorderOrOnTheWay(availability)) {
                xml.append("      <pickup>false</pickup>\n");
                xml.append("      <delivery>true</delivery>\n");
                xml.append("      <sales_notes>предоплата</sales_notes>\n");
            }

            if (isReserved(availability)) {
                xml.append("      <pickup>false</pickup>\n");
                xml.append("      <delivery>false</delivery>\n");
                xml.append("      <sales_notes>у резерві, уточнюйте наявність</sales_notes>\n");
            }

            // Назва
            String nameUk = firstNotBlank(p.getNameUk(), p.getOriginalName());
            String nameRu = firstNotBlank(p.getNameRu(), p.getOriginalName());

            xml.append("      <name>")
                    .append(XmlSafe.text(nameRu))
                    .append("</name>\n");

            xml.append("      <name_ua>")
                    .append(XmlSafe.text(nameUk))
                    .append("</name_ua>\n");

            // Ключові слова
            if (p.getKeywordsRu() != null && !p.getKeywordsRu().isBlank()) {
                xml.append("      <keywords>").append(XmlSafe.text(p.getKeywordsRu())).append("</keywords>\n");
            }
            if (p.getKeywordsUk() != null && !p.getKeywordsUk().isBlank()) {
                xml.append("      <keywords_ua>").append(XmlSafe.text(p.getKeywordsUk())).append("</keywords_ua>\n");
            }

            // Виробник
            if (p.getVendor() != null && !p.getVendor().isBlank()) {
                xml.append("      <vendor>").append(XmlSafe.text(p.getVendor())).append("</vendor>\n");
            }

            // Опис
            if (p.getDescriptionRu() != null) {
                xml.append("      <description>")
                        .append(XmlSafe.cdata(p.getDescriptionRu().replace("\n", "<br/>")))
                        .append("</description>\n");
            }

            if (p.getDescriptionUk() != null) {
                xml.append("      <description_ua>")
                        .append(XmlSafe.cdata(p.getDescriptionUk().replace("\n", "<br/>")))
                        .append("</description_ua>\n");
            }

            if (p.getTechnicalSpecs() != null) {
                for (Map.Entry<String, String> spec : p.getTechnicalSpecs().entrySet()) {
                    // Фільтруємо пусті значення, щоб не було порожніх тегів
                    if (spec.getValue() != null && !spec.getValue().isBlank()) {
                        xml.append("      <param name=\"")
                                .append(XmlSafe.attribute(spec.getKey()))
                                .append("\">")
                                .append(XmlSafe.text(spec.getValue()))
                                .append("</param>\n");
                    }
                }
            }

            if (p.getWarranty() != null && !p.getWarranty().isBlank()) {
                xml.append("      <param name=\"Гарантійний термін\">")
                        .append(XmlSafe.text(p.getWarranty()))
                        .append(" міс</param>\n");
            }

            xml.append("    </offer>\n");
        }
        xml.append("  </offers>\n");
        xml.append("</shop>\n");
        xml.append("</yml_catalog>");

        return xml.toString();
    }

    private Long getFeedCategoryId(String category) {
        if (category == null) {
            return 900000000L;
        }

        String value = category.trim().toLowerCase();

        return switch (value) {
            case "фотоелектричні модулі" -> 900000001L;
            case "акумулятори lv" -> 900000002L;
            case "акумулятори hv" -> 900000003L;
            case "гібридні інвертори" -> 900000004L;
            case "мережеві інвертори" -> 900000005L;
            case "автономні інвертори" -> 900000006L;
            case "сонячний кабель" -> 900000007L;
            case "bess" -> 900000008L;
            case "лічильники до інверторів" -> 900000009L;
            case "bms для акумуляторів" -> 900000010L;
            case "стійки для акумуляторів" -> 900000011L;
            case "комплектуючі bess" -> 900000012L;
            default -> 900009999L;
        };
    }

    private boolean isReadyToShip(String availability) {
        if (availability == null || availability.isBlank()) {
            return false;
        }

        String value = availability.toLowerCase().trim();

        return value.contains("в наявності")
                || value.contains("на складі");
    }

    private String mapAvailabilityForYml(String availability) {
        if (availability == null || availability.isBlank()) {
            return "false";
        }

        String value = availability.toLowerCase().trim();

        if (value.contains("в наявності") || value.contains("на складі")) {
            return "true";
        }

        if (value.contains("в дорозі")
                || value.contains("під замовлення")
                || value.contains("под заказ")
                || value.contains("резерв")) {
            return "false";
        }

        return "false";
    }

    private boolean isPreorderOrOnTheWay(String availability) {
        if (availability == null || availability.isBlank()) {
            return false;
        }

        String value = availability.toLowerCase().trim();

        return value.contains("в дорозі")
                || value.contains("під замовлення")
                || value.contains("под заказ");
    }

    private boolean isReserved(String availability) {
        if (availability == null || availability.isBlank()) {
            return false;
        }

        String value = availability.toLowerCase().trim();

        return value.contains("резерв");
    }

    private String firstNotBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }

        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }

        return "Товар";
    }
}