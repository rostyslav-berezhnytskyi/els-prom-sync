package com.els.promsync.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

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
                    int startRow = 1;
                    int endRow = 200;

                    String range = "'" + originalTitle + "'!A" + startRow + ":G" + endRow;

                    Set<Integer> hiddenRows = getHiddenRowNumbers(spreadsheetId, originalTitle);

                    ValueRange response = sheetsService.spreadsheets().values()
                            .get(spreadsheetId, range)
                            .execute();

                    List<List<Object>> values = response.getValues();

                    if (values == null || values.isEmpty()) {
                        System.out.println("Даних не знайдено на цій вкладці.");
                    } else {
                        System.out.println("✅ Зчитано рядків: " + values.size());

                        for (int i = 0; i < values.size(); i++) {
                            int realSheetRowNumber = startRow + i;
                            List<Object> row = values.get(i);

                            // Skip rows hidden manually by user or hidden by filter in Google Sheets.
                            if (hiddenRows.contains(realSheetRowNumber)) {
                                System.out.println("⏭ Skip hidden row: " + realSheetRowNumber + " | DATA: " + row);
                                continue;
                            }

                            // Skip headers, empty rows, category titles and other non-product rows.
                            if (!isProductRow(row)) {
                                System.out.println("⏭ Skip service/empty row: " + realSheetRowNumber + " | DATA: " + row);
                                continue;
                            }

                            productSyncService.previewRowPrice(row, cleanTitle, realSheetRowNumber);

                            // productSyncService.processRow(row, cleanTitle);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Помилка читання таблиці: " + e.getMessage());
        }
    }

    private Set<Integer> getHiddenRowNumbers(String spreadsheetId, String sheetTitle) throws IOException {
        Set<Integer> hiddenRows = new HashSet<>();

        Spreadsheet spreadsheet = sheetsService.spreadsheets()
                .get(spreadsheetId)
                .setRanges(List.of("'" + sheetTitle + "'"))
                .setIncludeGridData(true)
                .setFields("sheets(properties(title),data(rowMetadata(hiddenByUser,hiddenByFilter)))")
                .execute();

        for (Sheet sheet : spreadsheet.getSheets()) {
            if (!sheetTitle.equals(sheet.getProperties().getTitle())) {
                continue;
            }

            if (sheet.getData() == null) {
                continue;
            }

            for (GridData gridData : sheet.getData()) {
                List<DimensionProperties> rowMetadata = gridData.getRowMetadata();

                if (rowMetadata == null) {
                    continue;
                }

                for (int i = 0; i < rowMetadata.size(); i++) {
                    DimensionProperties row = rowMetadata.get(i);

                    boolean hiddenByUser = Boolean.TRUE.equals(row.getHiddenByUser());
                    boolean hiddenByFilter = Boolean.TRUE.equals(row.getHiddenByFilter());

                    if (hiddenByUser || hiddenByFilter) {
                        // Google API рахує з 0, а Google Sheets у UI показує рядки з 1
                        hiddenRows.add(i + 1);
                    }
                }
            }
        }

        return hiddenRows;
    }

    /**
     * Checks whether a Google Sheets row looks like a real product row.
     * This method filters out headers, empty rows, category titles and notes.
     */
    private boolean isProductRow(List<Object> row) {
        if (row == null || row.size() < 5) {
            return false;
        }

        String sku = row.get(0) != null ? row.get(0).toString().trim() : "";
        String name = row.get(1) != null ? row.get(1).toString().trim() : "";
        String availability = row.get(2) != null ? row.get(2).toString().trim().toLowerCase() : "";
        String priceWithoutVat = row.get(4) != null ? row.get(4).toString().trim() : "";

        if (sku.isBlank() || name.isBlank()) {
            return false;
        }

        if (sku.equalsIgnoreCase("Модель")) {
            return false;
        }

        if (availability.isBlank()) {
            return false;
        }

        if (priceWithoutVat.isBlank()) {
            return false;
        }

        return true;
    }
}
