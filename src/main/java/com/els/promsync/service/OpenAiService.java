package com.els.promsync.service;

import com.els.promsync.dto.AiProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    @Value("${openai.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public AiProductResponse enrichProduct(String originalName, String category) {
        String allowedKeys;
        String catLower = category.toLowerCase();

        // Динамічно підбираємо характеристики залежно від вкладки за точним словником Prom.ua
        if (catLower.contains("інвертор")) {
            allowedKeys = "['Виробник', 'Країна виробник', 'Кількість фаз живлення', 'Номінальна потужність', 'Максимальна потужність', 'Допустима пікова потужність', 'Номінальна вхідна напруга', 'Номінальна вихідна напруга', 'Форма вихідної напруги', 'Ступінь захисту IP', 'ККД, не менше', 'Кількість MPPT трекерів', 'Наявність Wi-Fi']";
        } else if (catLower.contains("акумулятор") || catLower.contains("bess")) {
            allowedKeys = "['Виробник', 'Країна виробник', 'Тип акумулятора', 'Ємність акумулятору', 'Напрацювання', 'Напруга', 'Струм заряду', 'Ступінь захисту IP', 'Мінімальна робоча температура', 'Максимальна робоча температура']";
        } else if (catLower.contains("модулі") || catLower.contains("панелі")) {
            allowedKeys = "['Виробник', 'Країна виробник', 'Номінальна потужність', 'Тип кристала', 'ККД, не менше']";
        } else {
            allowedKeys = "['Виробник', 'Матеріал', 'Призначення']";
        }

        String prompt = """
                Ти головний інженер та SEO-копірайтер компанії ELS (Energy Life Systems). 
                Проаналізуй технічну назву товару: "%s" з категорії "%s".
                
                Поверни результат ВИКЛЮЧНО у форматі JSON з такими полями:
                - "seoName": Приваблива комерційна назва українською мовою.
                - "seoDescription": Професійний SEO-опис у HTML-форматі (використовуй теги <p>, <ul>, <li>, <strong>). Структура: 
                   1) Короткий вступ. 
                   2) Маркований список <ul><li> "Головні переваги". 
                   3) Сфери застосування. 
                   4) Обов'язково додай фінальний абзац: "<strong>Команда ELS</strong> — це професійні інсталятори, які виконують монтаж систем енергонезалежності "під ключ" у Києві та області."
                - "keywords": Масив з 15-20 комерційних пошукових фраз (не окремих слів!). Включай запити типу "купити [назва]", "[назва] ціна Київ", "[категорія] для дому".
                - "specs": Об'єкт технічних характеристик. 
                ВАЖЛИВІ ПРАВИЛА ДЛЯ ХАРАКТЕРИСТИК: 
                1. Використовуй ТІЛЬКИ ключі з цього списку, без змін: %s. 
                2. Обов'язково додавай одиниці виміру ДО ЗНАЧЕНЬ (наприклад, ключ "Номінальна потужність", значення "6000 Вт" або ключ "Ємність акумулятору", значення "314 А. г").
                3. Якщо даних для ключа немає в назві — повністю ігноруй цей ключ, не вигадуй.
                - "vendor": Назва бренду-виробника, тільки одне слово.
                """.formatted(originalName, category, allowedKeys);

        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", new Object[]{ Map.of("role", "user", "content", prompt) },
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3
        );

        try {
            String responseStr = restClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            var rootNode = objectMapper.readTree(responseStr);
            String jsonContent = rootNode.path("choices").get(0).path("message").path("content").asText();
            return objectMapper.readValue(jsonContent, AiProductResponse.class);

        } catch (Exception e) {
            log.error("Помилка генерації AI для товару {}: {}", originalName, e.getMessage());
            return null;
        }
    }
}