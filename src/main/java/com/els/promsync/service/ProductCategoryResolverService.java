package com.els.promsync.service;

import org.springframework.stereotype.Service;

@Service
public class ProductCategoryResolverService {

    /**
     * Resolves our internal product category.
     *
     * Default rule: use dealer tab as category.
     * Exception rules are used only for mixed tabs.
     */
    public String resolve(String dealerTab, String sectionTitle, String sku, String productName) {
        String tab = normalize(dealerTab);
        String section = normalize(sectionTitle);
        String text = normalize(sku + " " + productName);

        if (tab.contains("акумулятори hv")) {
            if (containsAny(text, "стійка", "стойка", "rack", "hrack", "lrack", "qube")) {
                return "Стійки для акумуляторів";
            }

            if (containsAny(text, "без bms", "no bms", "по bms", "акумуляторна батарея")) {
                return "Акумулятори HV";
            }

            if (containsAny(text, "bms", "pdu", "модуль управління")) {
                return "BMS для акумуляторів";
            }

            return "Акумулятори HV";
        }

        if (tab.contains("мережеві")) {
            if (section.contains("лічильник")
                    || containsAny(text,
                    "лічильник",
                    "smart meter",
                    "meter",
                    "трансформатор",
                    "ct-set",
                    "dtsd",
                    "dtsu",
                    "sdm630",
                    "eastron",
                    "acrel")) {
                return "Лічильники до інверторів";
            }

            return "Мережеві інвертори";
        }

        if (tab.contains("bess")) {
            if (containsAny(text, "ems", "контролер", "шафа", "комут", "авр", "ats")) {
                return "Комплектуючі BESS";
            }

            return "BESS";
        }

        return dealerTab;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase().trim();
    }
}