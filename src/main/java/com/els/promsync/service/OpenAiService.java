package com.els.promsync.service;

import com.els.promsync.dto.AiProductResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import static com.els.promsync.config.AiPromptCatalog.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${openai.retry.first-delay-ms:3000}")
    private long firstDelayMs;

    @Value("${openai.retry.second-delay-ms:10000}")
    private long secondDelayMs;

    @Value("${openai.model:gpt-5.4}")
    private String openAiModel;

    @Value("${openai.temperature:0.1}")
    private double temperature;

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public AiProductResponse enrichProduct(String originalName, String category) {
        String catLower = category == null ? "" : category.toLowerCase();

        // Дефолтні значення
        String schemaToUse = "{ \"Виробник\": \"Назва бренду\" }";
        String keysToUse = "['Виробник']";
        String extraRules = "";

        if (catLower.contains("сонячний кабель")) {
            schemaToUse = SOLAR_CABLE_SCHEMA;
            keysToUse = SOLAR_CABLE_ALLOWED_KEYS;
            extraRules = SOLAR_CABLE_EXTRA_RULES;
        } else if (isCurrentTransformer(originalName)) {
            schemaToUse = CURRENT_TRANSFORMER_SCHEMA;
            keysToUse = CURRENT_TRANSFORMER_ALLOWED_KEYS;
            extraRules = CURRENT_TRANSFORMER_EXTRA_RULES;
        } else if (catLower.contains("лічильник")) {
            schemaToUse = SMART_METER_SCHEMA;
            keysToUse = SMART_METER_ALLOWED_KEYS;
            extraRules = SMART_METER_EXTRA_RULES;
        } else if (catLower.contains("bms")) {
            schemaToUse = BMS_SCHEMA;
            keysToUse = BMS_ALLOWED_KEYS;
            extraRules = BMS_EXTRA_RULES;
        } else if (catLower.contains("комплектуючі bess")) {
            schemaToUse = BESS_ACCESSORY_SCHEMA;
            keysToUse = BESS_ACCESSORY_ALLOWED_KEYS;
            extraRules = BESS_ACCESSORY_EXTRA_RULES;
        } else if (catLower.contains("bess")) {
            schemaToUse = BESS_SCHEMA;
            keysToUse = BESS_ALLOWED_KEYS;
            extraRules = BESS_EXTRA_RULES;
        } else if (catLower.contains("стійк") || catLower.contains("стойк") || catLower.contains("rack")) {
            schemaToUse = BATTERY_RACK_SCHEMA;
            keysToUse = BATTERY_RACK_ALLOWED_KEYS;
            extraRules = BATTERY_RACK_EXTRA_RULES;
        } else if (catLower.contains("інвертор")) {
            schemaToUse = INVERTER_SCHEMA;
            keysToUse = INVERTER_ALLOWED_KEYS;
        } else if (catLower.contains("акумулятор") || catLower.contains("батаре")) {
            schemaToUse = BATTERY_SCHEMA;
            keysToUse = BATTERY_ALLOWED_KEYS;
        } else if (catLower.contains("панел") || catLower.contains("модул")) {
            schemaToUse = PANEL_SCHEMA;
            keysToUse = PANEL_ALLOWED_KEYS;
        }

        String prompt = """
                Ти B2B маркетолог та технічний спеціаліст з обладнання для сонячної енергетики.
                Твоє завдання — створити преміальну, експертну картку товару для Prom.ua.
                Товар: "%s". Категорія: "%s".
                
                Поверни СУВОРИЙ JSON з такими полями:
                - "seoNameUa" та "seoNameRu": Комерційні назви (Укр та Рос).
                - "seoDescriptionUa" та "seoDescriptionRu": РОЗГОРНУТИЙ, ПРОДАЮЧИЙ SEO-опис у HTML (МІНІМУМ 4 абзаци!).
                    Структура опису має бути такою:
                  1. Короткий вступ з назви, типу або моделі товару: що це за товар і для чого він потрібен.
                  2. Розгорнутий опис технології (як саме цей пристрій працює і чому він надійний).
                  3. Детальний список <ul><li> з 5-7 пунктів з технічними та експлуатаційними перевагами.
                  4. Сфери застосування (приватні будинки, офіси, виробництва тощо).
                  5. Якщо доречно — короткий технічний блок з основними характеристиками, які можна визначити з назви товару або типових властивостей цього класу обладнання.
                  6. ПРО КОМПАНІЮ (ОБОВ'ЯЗКОВО): Розгорнутий абзац про те, що компанія ELS (Energy Life Systems) надає повний цикл послуг. Напиши, що ви проектуєте, підбираєте, постачаєте, монтуєте та налаштовуєте обладнання для енергонезалежності. Обов'язково перерахуй ваші напрямки: гібридні інвертори, акумуляторні батареї, сонячні панелі, сонячні станції та системи BESS. Наголоси, що ви реалізуєте проекти будь-якої складності як для приватних користувачів (квартири, приватні будинки), так і для бізнесу (виробництва, заводи, офіси), а також спеціалізовані рішення для багатоквартирних будинків (ОСББ). Зроби сильний заклик до співпраці!
                - "keywordsUa" та "keywordsRu": Масиви по 15 потужних комерційних пошукових фраз ("купити [товар] київ", "[бренд] ціна", "монтаж [товар]").
                - "vendor": Назва бренду.
                - "specs": Об'єкт характеристик.
                
                ПРАВИЛА ДЛЯ ПОЛЯ "specs" (КРИТИЧНО ВАЖЛИВО):
                Я надаю тобі JSON-шаблон. КЛЮЧ — це назва характеристики, ЗНАЧЕННЯ — це інструкція для тебе, ЯК САМЕ треба заповнити це поле.
                Твоя задача: повернути об'єкт "specs" з технічними характеристиками товару. Використовуй тільки ті ключі з шаблону, для яких можеш визначити реальне значення. Не обов'язково повертати всі ключі з шаблону.
                
                1. ТИ МАЄШ ПРАВО ВИКОРИСТОВУВАТИ ТІЛЬКИ ЦІ КЛЮЧІ: %s
                2. СУВОРО дотримуйся інструкцій у значеннях шаблону.
                3. Заповнюй характеристику, якщо вона прямо вказана у назві товару, у вхідних даних, однозначно витягується з моделі або надійно відома для конкретної моделі товару.
                4. Якщо ти точно не знаєш реального значення для певного ключа — ПРОСТО ВИДАЛИ ЦЕЙ КЛЮЧ з фінального JSON. Не вгадуй навмання.
                5. У полі "specs" не пиши значення типу "уточнюється", "залежить від конфігурації", "перевіряється за документацією". Для specs краще видалити ключ, ніж писати невизначене значення.
                6. Габарити, вагу та інші фізичні параметри додавай тільки якщо вони прямо вказані у назві товару, технічній моделі або надійно відомі для точно визначеної моделі. Якщо не впевнений — видали відповідний ключ.
                
                 ДОДАТКОВІ ПРАВИЛА ДЛЯ ЦІЄЇ КАТЕГОРІЇ:
                %s
                
                ОСЬ ТВІЙ ШАБЛОН-ІНСТРУКЦІЯ ДЛЯ "specs":
                %s
                
                ЗАГАЛЬНІ ПРАВИЛА ДЛЯ ОПИСУ:
                %s
                """.formatted(originalName, category, keysToUse, extraRules, schemaToUse, GENERAL_DESCRIPTION_RULES);

        Map<String, Object> requestBody = Map.of(
                "model", openAiModel,
                "messages", new Object[]{ Map.of("role", "user", "content", prompt) },
                "response_format", Map.of("type", "json_object"),
                "temperature", temperature
        );

        try {
            return executeWithRetry(
                    () -> callOpenAi(requestBody),
                    "OpenAI enrich product: " + originalName
            );
        } catch (Exception e) {
            log.error("Помилка генерації AI для товару {} після retry", originalName, e);
            return null;
        }
    }

    private boolean isCurrentTransformer(String productName) {
        if (productName == null) {
            return false;
        }

        String value = productName.toLowerCase();

        return value.contains("трансформатор")
                || value.contains("transformer")
                || value.contains("ct-set")
                || value.contains("ct_")
                || value.contains("/333mv");
    }

    private AiProductResponse callOpenAi(Map<String, Object> requestBody) throws Exception {
        String responseStr = restClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        var rootNode = objectMapper.readTree(responseStr);

        if (!rootNode.has("choices") || rootNode.path("choices").isEmpty()) {
            throw new IllegalStateException("OpenAI response has no choices");
        }

        String jsonContent = rootNode.path("choices").get(0).path("message").path("content").asText();

        if (jsonContent == null || jsonContent.isBlank()) {
            throw new IllegalStateException("OpenAI response content is empty");
        }

        return objectMapper.readValue(jsonContent, AiProductResponse.class);
    }

    private <T> T executeWithRetry(OpenAiOperation<T> operation, String operationName) throws Exception {
        int attempts = Math.max(1, maxAttempts);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                boolean lastAttempt = attempt == attempts;

                if (!isRetryableOpenAiError(e) || lastAttempt) {
                    throw e;
                }

                long sleepMs = attempt == 1 ? firstDelayMs : secondDelayMs;

                log.warn(
                        "{} failed. Attempt {}/{}. Retry in {} ms. Error: {}",
                        operationName,
                        attempt,
                        attempts,
                        sleepMs,
                        e.getMessage()
                );

                sleepBeforeRetry(sleepMs, operationName);
            }
        }

        throw new IllegalStateException(operationName + " failed unexpectedly");
    }

    private boolean isRetryableOpenAiError(Exception e) {
        if (e instanceof RestClientResponseException responseException) {
            int statusCode = responseException.getStatusCode().value();

            return statusCode == 408
                    || statusCode == 409
                    || statusCode == 429
                    || statusCode >= 500;
        }

        if (e instanceof ResourceAccessException) {
            return true;
        }

        if (e instanceof JsonProcessingException) {
            return true;
        }

        if (e instanceof IllegalStateException) {
            return true;
        }

        return false;
    }

    private void sleepBeforeRetry(long sleepMs, String operationName) throws Exception {
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operationName + " interrupted during retry", e);
        }
    }

    @FunctionalInterface
    private interface OpenAiOperation<T> {
        T execute() throws Exception;
    }
}