package com.els.promsync;

import com.els.promsync.service.GoogleSheetsService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
//@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class ElsPromSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElsPromSyncApplication.class, args);
    }

    // Цей код виконається автоматично після успішного старту Spring Boot
    @Bean
    CommandLineRunner run(GoogleSheetsService googleSheetsService) {
        return args -> {
            System.out.println("--- Починаємо тестове зчитування з Google Sheets ---");
            googleSheetsService.testReadSheet();
            System.out.println("--- Тест завершено ---");
        };
    }
}
