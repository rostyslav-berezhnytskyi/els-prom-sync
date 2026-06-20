package com.els.promsync.service;

import com.els.promsync.entity.Product;
import com.els.promsync.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductMediaSyncService {

    private final ProductRepository productRepository;
    private final ProductImageService productImageService;

    /**
     * Scans product image folders and saves public image URLs into DB.
     *
     * If images are found in folder, imageUrls is overwritten.
     * If no images are found, existing imageUrls is kept unchanged.
     */
    @Transactional
    public void syncImageUrlsFromFolders() {
        List<Product> products = productRepository.findByActiveFromDealerTrue();

        for (Product product : products) {
            List<String> foundUrls = productImageService.scanImageUrlsFromFolder(product);

            if (foundUrls.isEmpty()) {
                continue;
            }

            String newImageUrls = productImageService.toImageUrlsString(foundUrls);
            String oldImageUrls = product.getImageUrls() == null ? "" : product.getImageUrls().trim();

            if (oldImageUrls.equals(newImageUrls)) {
                continue;
            }

            product.setImageUrls(newImageUrls);
            productRepository.save(product);

            System.out.println("IMAGE URLS UPDATED | SKU: " + product.getSku() + " | COUNT: " + foundUrls.size());
        }
    }
}
