package com.els.promsync.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    // Spring автоматично підставить сюди бін, який ми створили в GoogleSheetsConfig
    private final Sheets sheetsService;
    private final ProductSyncService productSyncService;


    @Value("${google.sheets.spreadsheet-id}")
    private String spreadsheetId;

//    public void testReadSheet() {
//        try {
//            // Вказуємо Назву вкладки і діапазон комірок, які хочемо прочитати.
//            // Зверни увагу: назва вкладки має бути ТОЧНО такою, як в Excel внизу
//            String range = "'Акумулятори LV'!A12:G13";
//
//            ValueRange response = sheetsService.spreadsheets().values()
//                    .get(spreadsheetId, range)
//                    .execute();
//
//            List<List<Object>> values = response.getValues();
//
//            if (values == null || values.isEmpty()) {
//                System.out.println("Даних не знайдено.");
//            } else {
//                System.out.println("✅ Успішно прочитано дані з таблиці!");
//                for (List<Object> row : values) {
//                    // Виводимо кожен рядок у консоль
//                    System.out.println(row);
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("❌ Помилка читання таблиці: " + e.getMessage());
//        }
//    }

    public void testReadSheet() {
        try {
            System.out.println("--- ДІАГНОСТИКА ПІДКЛЮЧЕННЯ ---");
            com.google.api.services.sheets.v4.model.Spreadsheet spreadsheet =
                    sheetsService.spreadsheets().get(spreadsheetId).execute();

            System.out.println("✅ Документ знайдено!");

            // Список вкладок, які ми дозволяємо обробляти (назви без пробілів у кінці)
            List<String> allowedTabs = List.of(
                    "Гібридні інвертори", "Акумулятори LV", "Фотоелектричні модулі"
            );

            // Перебираємо абсолютно всі вкладки у файлі
            for (com.google.api.services.sheets.v4.model.Sheet sheet : spreadsheet.getSheets()) {
                String originalTitle = sheet.getProperties().getTitle();
                String cleanTitle = originalTitle.trim(); // Видаляємо зайві пробіли по краях

                // Перевіряємо, чи є ця вкладка в нашому білому списку
                if (allowedTabs.contains(cleanTitle)) {
                    System.out.println("\n➡ Починаємо читати вкладку: [" + cleanTitle + "]");

                    // Формуємо діапазон. Беремо оригінальну назву (з пробілом), інакше Google видасть помилку.
                    // Читаємо з 12 по 13 рядок ДЛЯ ТЕСТУ (потім заміниш на A12:G100)
                    String range = "'" + originalTitle + "'!A12:G12";

                    ValueRange response = sheetsService.spreadsheets().values()
                            .get(spreadsheetId, range)
                            .execute();

                    List<List<Object>> values = response.getValues();

                    if (values == null || values.isEmpty()) {
                        System.out.println("Даних не знайдено на цій вкладці.");
                    } else {
                        System.out.println("✅ Зчитано рядків: " + values.size());
                        for (List<Object> row : values) {
                            // Передаємо ЧИСТУ назву категорії в процесор
                            productSyncService.processRow(row, cleanTitle);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Помилка читання таблиці: " + e.getMessage());
        }
    }
}
