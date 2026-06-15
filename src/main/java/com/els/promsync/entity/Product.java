package com.els.promsync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "products")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========================================
    // БЛОК 1: ДАНІ З ТАБЛИЦІ ДИЛЕРА (Оновлюються щодня)
    // ==========================================
    @Column(unique = true, nullable = false)
    private String sku; // Артикул (RW-F16)

    private String originalName; // Оригінальна назва дилера (щоб ти міг звірити)
    private String dealerCategory; // Категорія з вкладки (Акумулятори LV)
    @Column(precision = 10, scale = 4)
    private BigDecimal basePriceUsd; // Ціна без ПДВ у $
    private String availability; // Статус (в дорозі, в наявності)
    private String warranty; // Гарантія (5+5, 5 років)

    // ==========================================
    // БЛОК 2: НАША КОМЕРЦІЯ (Розраховується програмою)
    // ==========================================
    private BigDecimal priceUah; // Фінальна ціна для продажу на Промі

    // ==========================================
    // БЛОК 3: ДАНІ ДЛЯ PROM.UA (Генерує AI один раз)
    // ==========================================
    private String vendor; // Виробник (Deye, Solis - витягне AI з назви)

    @Column(columnDefinition = "TEXT")
    private String nameUk; // SEO-оптимізована назва (від AI)

    @Column(columnDefinition = "TEXT")
    private String nameRu;

    @Column(columnDefinition = "TEXT")
    private String descriptionUk; // Гарний опис з нашими перевагами (від AI)

    @Column(columnDefinition = "TEXT")
    private String descriptionRu;

    @Column(columnDefinition = "TEXT")
    private String keywordsUk; // Ключові слова через кому (від AI)

    @Column(columnDefinition = "TEXT")
    private String keywordsRu;

    // ==========================================
    // БЛОК 4: МЕДІА (Збираємо локально)
    // ==========================================
    @Column(columnDefinition = "TEXT")
    private String imageUrls; // Посилання на фото через кому (url1,url2)

    private LocalDateTime lastUpdated;

    // Поле для всіх технічних характеристик
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> technicalSpecs = new HashMap<>();

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        lastUpdated = LocalDateTime.now();
    }
}