package com.els.promsync.service;

import com.els.promsync.dto.SyncChangeType;
import com.els.promsync.dto.SyncReport;
import com.els.promsync.entity.Product;
import com.els.promsync.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductImageAuditService {

    private final ProductRepository productRepository;
    private final ProductImageService productImageService;

    public void addMissingImagesToReport(SyncReport report) {
        if (report == null) {
            return;
        }

        for (Product product : productRepository.findByActiveFromDealerTrue()) {
            if (!productImageService.findImageUrls(product).isEmpty()) {
                continue;
            }

            report.addChange(
                    SyncChangeType.MISSING_IMAGE,
                    product.getSku(),
                    product.getOriginalName(),
                    product.getDealerCategory(),
                    "",
                    "немає фото"
            );
        }
    }
}
