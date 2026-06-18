package com.els.promsync;

import com.els.promsync.service.GoogleSheetsService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.els.promsync.dto.SyncReport;
import com.els.promsync.repository.ProductRepository;
import com.els.promsync.service.TelegramNotificationService;

@SpringBootApplication
//@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class ElsPromSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElsPromSyncApplication.class, args);
    }

    // Цей код виконається автоматично після успішного старту Spring Boot
    @Bean
    CommandLineRunner run(
            GoogleSheetsService googleSheetsService,
            TelegramNotificationService telegramNotificationService,
            ProductRepository productRepository
    ) {
        return args -> {
            System.out.println("--- Починаємо тестове зчитування з Google Sheets ---");

            SyncReport report = googleSheetsService.testReadSheet();

            report.markYmlFeedReady(productRepository.count());

            telegramNotificationService.sendSyncReport(report);

            System.out.println("--- Тест завершено ---");
        };
    }
}
