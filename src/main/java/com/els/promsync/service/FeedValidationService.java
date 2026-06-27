package com.els.promsync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class FeedValidationService {

    private static final int MAX_PICTURES_PER_OFFER = 10;

    public FeedValidationResult validate(String xml) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (xml == null || xml.isBlank()) {
            errors.add("YML feed is empty");
            return new FeedValidationResult(errors, warnings);
        }

        Document document;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);

            // DOCTYPE у Prom/YML нормальний, бо в нас є:
            // <!DOCTYPE yml_catalog SYSTEM "shops.dtd">
            // Тому НЕ забороняємо DOCTYPE повністю.

            // Але забороняємо підтягування зовнішніх entity/DTD,
            // щоб парсер не ліз в інтернет за shops.dtd і не відкривав зайвих зовнішніх ресурсів.
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);

            document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            errors.add("XML is not valid: " + e.getMessage());
            return new FeedValidationResult(errors, warnings);
        }

        Element root = document.getDocumentElement();

        if (root == null || !"yml_catalog".equals(root.getNodeName())) {
            errors.add("Root tag must be <yml_catalog>");
            return new FeedValidationResult(errors, warnings);
        }

        var categories = document.getElementsByTagName("category");
        if (categories.getLength() == 0) {
            errors.add("No <category> tags found");
        }

        var offers = document.getElementsByTagName("offer");
        if (offers.getLength() == 0) {
            errors.add("No <offer> tags found");
            return new FeedValidationResult(errors, warnings);
        }

        Set<String> offerIds = new HashSet<>();

        for (int i = 0; i < offers.getLength(); i++) {
            Element offer = (Element) offers.item(i);

            String offerId = offer.getAttribute("id");

            if (isBlank(offerId)) {
                errors.add("Offer #" + (i + 1) + " has empty id");
            } else if (!offerIds.add(offerId)) {
                errors.add("Duplicate offer id: " + offerId);
            }

            String label = isBlank(offerId) ? "offer #" + (i + 1) : "offer id=" + offerId;

            validateRequiredText(errors, offer, "name", label);
            validateRequiredText(errors, offer, "name_ua", label);
            validateRequiredText(errors, offer, "categoryId", label);
            validatePrice(errors, offer, label);
            validatePictures(errors, warnings, offer, label);
        }

        return new FeedValidationResult(errors, warnings);
    }

    public void validateOrThrow(String xml) {
        FeedValidationResult result = validate(xml);

        if (result.hasErrors()) {
            String message = "Prom feed validation failed:\n"
                    + String.join("\n", result.errors().stream().limit(50).toList());

            throw new IllegalStateException(message);
        }

        if (result.hasWarnings()) {
            log.warn("Prom feed validation warnings:\n{}",
                    String.join("\n", result.warnings().stream().limit(50).toList()));
        }
    }

    private void validateRequiredText(List<String> errors, Element offer, String tagName, String label) {
        String value = getText(offer, tagName);

        if (isBlank(value)) {
            errors.add(label + " has empty <" + tagName + ">");
        }
    }

    private void validatePrice(List<String> errors, Element offer, String label) {
        String value = getText(offer, "price");

        if (isBlank(value)) {
            errors.add(label + " has empty <price>");
            return;
        }

        try {
            BigDecimal price = new BigDecimal(value.trim().replace(",", "."));

            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(label + " has price <= 0: " + value);
            }
        } catch (Exception e) {
            errors.add(label + " has invalid price: " + value);
        }
    }

    private void validatePictures(
            List<String> errors,
            List<String> warnings,
            Element offer,
            String label
    ) {
        var pictures = offer.getElementsByTagName("picture");

        if (pictures.getLength() == 0) {
            warnings.add(label + " has no pictures");
            return;
        }

        if (pictures.getLength() > MAX_PICTURES_PER_OFFER) {
            errors.add(label + " has more than " + MAX_PICTURES_PER_OFFER + " pictures: " + pictures.getLength());
        }

        for (int i = 0; i < pictures.getLength(); i++) {
            String pictureUrl = pictures.item(i).getTextContent();

            if (isBlank(pictureUrl)) {
                errors.add(label + " has empty <picture>");
            }
        }
    }

    private String getText(Element parent, String tagName) {
        var nodes = parent.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            return "";
        }

        return nodes.item(0).getTextContent();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record FeedValidationResult(
            List<String> errors,
            List<String> warnings
    ) {
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
    }
}
