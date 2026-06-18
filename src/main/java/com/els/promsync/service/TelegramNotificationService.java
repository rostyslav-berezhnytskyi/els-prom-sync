package com.els.promsync.service;

import com.els.promsync.dto.SyncChangeType;
import com.els.promsync.dto.SyncItemChange;
import com.els.promsync.dto.SyncReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelegramNotificationService {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3500;
    private static final int MAX_ITEMS_PER_SECTION = 30;

    private final RestClient restClient = RestClient.create();

    @Value("${telegram.enabled:false}")
    private boolean telegramEnabled;

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.chat-id:}")
    private String chatId;

    public void sendSyncReport(SyncReport report) {
        if (!telegramEnabled) {
            return;
        }

        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            log.warn("Telegram notification is enabled, but bot token or chat id is empty");
            return;
        }

        String message = buildMessage(report);
        sendLongMessage(message);
    }

    private String buildMessage(SyncReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append(report.getErrorCount() == 0 ? "🟢" : "🟠")
                .append(" ELS Prom Sync завершено\n\n");

        sb.append("Час старту: ")
                .append(formatTime(report.getStartedAt()))
                .append("\n");

        sb.append("Google Sheets: ")
                .append(report.isGoogleSheetsReadSuccess() ? "✅ зчитано успішно" : "❌ помилка")
                .append("\n");

        sb.append("YML для Prom: ")
                .append(report.isYmlFeedReady() ? "✅ готовий" : "⚠️ не перевірено")
                .append("\n");

        if (report.isYmlFeedReady()) {
            sb.append("Товарів у базі/фіді: ")
                    .append(report.getYmlProductsCount())
                    .append("\n");
        }

        sb.append("\n📊 Підсумок:\n");
        sb.append("Оброблено рядків: ").append(report.getProcessedRows()).append("\n");
        sb.append("Пропущено рядків: ").append(report.getSkippedRows()).append("\n");
        sb.append("Нові товари: ").append(report.countChanges(SyncChangeType.NEW_PRODUCT)).append("\n");
        sb.append("Зміни цін дилера: ").append(report.countChanges(SyncChangeType.PRICE_CHANGED)).append("\n");
        sb.append("Зміни наявності: ").append(report.countChanges(SyncChangeType.AVAILABILITY_CHANGED)).append("\n");
        sb.append("Зміни назв: ").append(report.countChanges(SyncChangeType.NAME_CHANGED)).append("\n");
        sb.append("Без змін: ").append(report.getUnchangedProducts()).append("\n");
        sb.append("Помилки: ").append(report.getErrorCount()).append("\n");

        appendSection(sb, "🆕 Нові товари", report.getChanges(), SyncChangeType.NEW_PRODUCT);
        appendSection(sb, "💲 Зміни цін дилера", report.getChanges(), SyncChangeType.PRICE_CHANGED);
        appendSection(sb, "📦 Зміни наявності", report.getChanges(), SyncChangeType.AVAILABILITY_CHANGED);
        appendSection(sb, "✏️ Зміни назв", report.getChanges(), SyncChangeType.NAME_CHANGED);

        if (!report.getErrors().isEmpty()) {
            sb.append("\n⚠️ Помилки:\n");

            int limit = Math.min(report.getErrors().size(), MAX_ITEMS_PER_SECTION);
            for (int i = 0; i < limit; i++) {
                sb.append("- ").append(report.getErrors().get(i)).append("\n");
            }

            if (report.getErrors().size() > limit) {
                sb.append("...і ще ")
                        .append(report.getErrors().size() - limit)
                        .append(" помилок\n");
            }
        }

        return sb.toString();
    }

    private void appendSection(
            StringBuilder sb,
            String title,
            List<SyncItemChange> changes,
            SyncChangeType type
    ) {
        List<SyncItemChange> filtered = changes.stream()
                .filter(change -> change.type() == type)
                .toList();

        if (filtered.isEmpty()) {
            return;
        }

        sb.append("\n").append(title).append(":\n");

        int limit = Math.min(filtered.size(), MAX_ITEMS_PER_SECTION);

        for (int i = 0; i < limit; i++) {
            SyncItemChange change = filtered.get(i);

            sb.append("- ")
                    .append(change.sku())
                    .append(" | ")
                    .append(change.productName());

            if (change.oldValue() != null && !change.oldValue().isBlank()
                    || change.newValue() != null && !change.newValue().isBlank()) {
                sb.append("\n  ")
                        .append(nullToDash(change.oldValue()))
                        .append(" → ")
                        .append(nullToDash(change.newValue()));
            }

            sb.append("\n");
        }

        if (filtered.size() > limit) {
            sb.append("...і ще ")
                    .append(filtered.size() - limit)
                    .append(" позицій\n");
        }
    }

    private void sendLongMessage(String text) {
        for (int start = 0; start < text.length(); start += TELEGRAM_MESSAGE_LIMIT) {
            int end = Math.min(start + TELEGRAM_MESSAGE_LIMIT, text.length());
            sendMessage(text.substring(start, end));
        }
    }

    private void sendMessage(String text) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            restClient.post()
                    .uri(url)
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", text,
                            "disable_web_page_preview", true
                    ))
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception e) {
            log.warn("Cannot send Telegram notification: {}", e.getMessage());
        }
    }

    private String formatTime(java.time.LocalDateTime time) {
        if (time == null) {
            return "-";
        }

        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String nullToDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        return value;
    }
}
