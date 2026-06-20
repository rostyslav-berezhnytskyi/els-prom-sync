package com.els.promsync.service;

import com.els.promsync.entity.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductImageService {

    @Value("${media.products-dir:/home/admin_els/els-media/products}")
    private String productsDir;

    @Value("${media.public-base-url:}")
    private String publicBaseUrl;

    @Value("${media.max-images-per-product:10}")
    private int maxImagesPerProduct;

    /**
     * Used by feed generation.
     * First uses URLs stored in DB.
     * If DB is empty, falls back to folder scan.
     */
    public List<String> findImageUrls(Product product) {
        List<String> storedUrls = getStoredImageUrls(product);

        if (!storedUrls.isEmpty()) {
            return storedUrls;
        }

        return scanImageUrlsFromFolder(product);
    }

    /**
     * Reads image URLs already stored in DB.
     */
    public List<String> getStoredImageUrls(Product product) {
        if (product == null || product.getImageUrls() == null || product.getImageUrls().isBlank()) {
            return List.of();
        }

        return parseManualImageUrls(product.getImageUrls());
    }

    /**
     * Scans local/server folders and builds public URLs.
     */
    public List<String> scanImageUrlsFromFolder(Product product) {
        if (product == null) {
            return List.of();
        }

        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return List.of();
        }

        String categoryFolder = getCategoryFolder(product.getDealerCategory());
        String skuFolder = toSafeFolderName(product.getSku());

        if (skuFolder.isBlank()) {
            return List.of();
        }

        Path productDir = Path.of(productsDir, categoryFolder, skuFolder);

        System.out.println("IMAGE SEARCH DIR: " + productDir);

        if (!Files.isDirectory(productDir)) {
            return List.of();
        }

        try (var stream = Files.list(productDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isImageFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(maxImagesPerProduct)
                    .map(path -> buildPublicUrl(categoryFolder, skuFolder, path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public String toImageUrlsString(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return "";
        }

        return String.join(", ", imageUrls);
    }

    public String toSafeFolderName(String value) {
        if (value == null) {
            return "";
        }

        String normalized = replaceVisualCyrillicWithLatin(value);

        return normalized.trim()
                .replaceAll("[/\\\\]+", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String getCategoryFolder(String category) {
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

    private List<String> parseManualImageUrls(String imageUrls) {
        return Arrays.stream(imageUrls.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .limit(maxImagesPerProduct)
                .toList();
    }

    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();

        return fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png")
                || fileName.endsWith(".webp");
    }

    private String buildPublicUrl(String categoryFolder, String skuFolder, String fileName) {
        String encodedCategory = UriUtils.encodePathSegment(categoryFolder, StandardCharsets.UTF_8);
        String encodedSku = UriUtils.encodePathSegment(skuFolder, StandardCharsets.UTF_8);
        String encodedFileName = UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8);

        return publicBaseUrl.replaceAll("/+$", "")
                + "/"
                + encodedCategory
                + "/"
                + encodedSku
                + "/"
                + encodedFileName;
    }

    private String replaceVisualCyrillicWithLatin(String value) {
        return value
                .replace('А', 'A')
                .replace('В', 'B')
                .replace('Е', 'E')
                .replace('К', 'K')
                .replace('М', 'M')
                .replace('Н', 'H')
                .replace('О', 'O')
                .replace('Р', 'P')
                .replace('С', 'C')
                .replace('Т', 'T')
                .replace('Х', 'X')
                .replace('У', 'Y')
                .replace('І', 'I')

                .replace('а', 'a')
                .replace('в', 'b')
                .replace('е', 'e')
                .replace('к', 'k')
                .replace('м', 'm')
                .replace('н', 'h')
                .replace('о', 'o')
                .replace('р', 'p')
                .replace('с', 'c')
                .replace('т', 't')
                .replace('х', 'x')
                .replace('у', 'y')
                .replace('і', 'i');
    }
}