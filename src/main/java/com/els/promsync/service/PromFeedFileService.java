package com.els.promsync.service;

import com.els.promsync.controller.PromFeedController;
import com.els.promsync.dto.SyncReport;
import com.els.promsync.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class PromFeedFileService {

    private final PromFeedController promFeedController;
    private final ProductRepository productRepository;

    @Value("${feed.output-file:/home/admin_els/els-media/feed/prom.xml}")
    private String outputFile;

    public void writePromFeedFile(SyncReport report) {
        try {
            String xml = promFeedController.generatePromFeed();

            Path path = Path.of(outputFile);

            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path, xml, StandardCharsets.UTF_8);

            if (report != null) {
                report.markYmlFeedReady(productRepository.countByActiveFromDealerTrue());
            }

            System.out.println("PROM XML FILE WRITTEN: " + path);

        } catch (Exception e) {
            if (report != null) {
                report.addError("Помилка генерації prom.xml: " + e.getMessage());
            }

            System.err.println("❌ Помилка генерації prom.xml: " + e.getMessage());
        }
    }
}
