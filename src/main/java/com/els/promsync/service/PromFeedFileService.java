package com.els.promsync.service;

import com.els.promsync.controller.PromFeedController;
import com.els.promsync.dto.SyncReport;
import com.els.promsync.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromFeedFileService {

    private final PromFeedController promFeedController;
    private final ProductRepository productRepository;
    private final FeedValidationService feedValidationService;

    @Value("${feed.output-file:/home/admin_els/els-media/feed/prom.xml}")
    private String outputFile;

    public void writePromFeedFile(SyncReport report) {
        try {
            String xml = promFeedController.generatePromFeed();

            FeedValidationService.FeedValidationResult validationResult =
                    feedValidationService.validate(xml);

            if (validationResult.hasErrors()) {
                String errorMessage = "Prom feed validation failed. prom.xml will not be updated.";

                log.error(errorMessage);
                validationResult.errors().forEach(error -> log.error("FEED VALIDATION ERROR: {}", error));

                if (report != null) {
                    report.addError(errorMessage);

                    validationResult.errors().stream()
                            .limit(20)
                            .forEach(error -> report.addError("Feed validation: " + error));

                    if (validationResult.errors().size() > 20) {
                        report.addError("Feed validation: і ще "
                                + (validationResult.errors().size() - 20)
                                + " помилок");
                    }
                }

                return;
            }

            if (validationResult.hasWarnings()) {
                validationResult.warnings()
                        .forEach(warning -> log.warn("FEED VALIDATION WARNING: {}", warning));
            }

            Path path = Path.of(outputFile);

            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            writeAtomically(path, xml);

            if (report != null) {
                report.markYmlFeedReady(productRepository.countByActiveFromDealerTrue());
            }

            System.out.println("PROM XML FILE WRITTEN: " + path);

        } catch (Exception e) {
            if (report != null) {
                report.addError("Помилка генерації prom.xml: " + e.getMessage());
            }

            System.err.println("❌ Помилка генерації prom.xml: " + e.getMessage());
            log.error("Cannot generate prom.xml", e);
        }
    }

    private void writeAtomically(Path path, String xml) throws Exception {
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");

        Files.writeString(
                tempPath,
                xml,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        try {
            Files.move(
                    tempPath,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(
                    tempPath,
                    path,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}