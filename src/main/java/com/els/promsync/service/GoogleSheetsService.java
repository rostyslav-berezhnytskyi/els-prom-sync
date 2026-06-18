package com.els.promsync.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.els.promsync.dto.SyncReport;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private static final int START_ROW = 47;
    private static final int END_ROW = 47;

    private static final List<String> ALLOWED_TABS = List.of(
//            "Фотоелектричні модулі",
            "Мережеві Інвертори"
//            "Сонячний кабель",
//            "Гібридні інвертори",
//            "Акумулятори LV",
//            "Акумулятори HV",
//            "BESS",
//            "Автономні Інвертори",
            // "Аксесуари" // add later if needed
    );

    private final Sheets sheetsService;
    private final ProductSyncService productSyncService;

    @Value("${google.sheets.spreadsheet-id}")
    private String spreadsheetId;

    /**
     * Temporary entry point for dealer sheet sync.
     * Later we can rename this method to syncDealerSheet().
     */
    public SyncReport testReadSheet() {
        return syncDealerSheet();
    }

    /**
     * Reads all allowed dealer tabs and sends product rows to ProductSyncService.
     */
    public SyncReport syncDealerSheet() {
        SyncReport report = new SyncReport();

        try {
            System.out.println("--- ДІАГНОСТИКА ПІДКЛЮЧЕННЯ ---");

            Spreadsheet spreadsheet = sheetsService.spreadsheets()
                    .get(spreadsheetId)
                    .execute();

            System.out.println("✅ Документ знайдено!");

            for (Sheet sheet : spreadsheet.getSheets()) {
                String originalTitle = sheet.getProperties().getTitle();
                String cleanTitle = originalTitle.trim();

                if (!isAllowedTab(cleanTitle)) {
                    continue;
                }

                processSheet(originalTitle, cleanTitle, report);
            }

            report.markGoogleSheetsReadSuccess();

        } catch (IOException e) {
            System.err.println("❌ Помилка читання таблиці: " + e.getMessage());
            report.addError("Помилка читання Google Sheets: " + e.getMessage());
        } finally {
            report.finish();
        }

        return report;
    }

    /**
     * Reads one Google Sheets tab and processes its product rows.
     */
    private void processSheet(String originalTitle, String cleanTitle, SyncReport report) throws IOException {
        System.out.println("\n➡ Починаємо читати вкладку: [" + cleanTitle + "]");

        Set<Integer> hiddenRows = getHiddenRowNumbers(spreadsheetId, originalTitle);
        List<List<Object>> values = readSheetValues(originalTitle, START_ROW, END_ROW);

        if (values == null || values.isEmpty()) {
            System.out.println("Даних не знайдено на цій вкладці.");
            return;
        }

        System.out.println("✅ Зчитано рядків: " + values.size());

        processRows(cleanTitle, values, hiddenRows, report);
    }

    /**
     * Reads values from one tab.
     */
    private List<List<Object>> readSheetValues(String originalTitle, int startRow, int endRow) throws IOException {
        String range = "'" + originalTitle + "'!A" + startRow + ":G" + endRow;

        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        return response.getValues();
    }

    /**
     * Filters rows and sends real product rows to ProductSyncService.
     */
    private void processRows(String tabTitle, List<List<Object>> values, Set<Integer> hiddenRows, SyncReport report) {
        for (int i = 0; i < values.size(); i++) {
            int realSheetRowNumber = START_ROW + i;
            List<Object> row = values.get(i);

            if (hiddenRows.contains(realSheetRowNumber)) {
                System.out.println("⏭ Skip hidden row: " + realSheetRowNumber + " | DATA: " + row);
                report.addSkippedRow();
                continue;
            }

            if (!isProductRow(row, tabTitle)) {
                System.out.println("⏭ Skip service/empty row: " + realSheetRowNumber + " | DATA: " + row);
                report.addSkippedRow();
                continue;
            }

            System.out.println(
                    "IMPORT | ROW: " + realSheetRowNumber +
                            " | TAB: " + tabTitle +
                            " | SKU: " + getCell(row, 0)
            );

            report.addProcessedRow();

            try {
                productSyncService.processRow(row, tabTitle, report);
            } catch (Exception e) {
                String sku = getCell(row, 0);
                String name = getCell(row, 1);

                report.addError("Помилка обробки товару: " + sku + " | " + name + " | " + e.getMessage());
            }
        }
    }

    private boolean isAllowedTab(String tabTitle) {
        return ALLOWED_TABS.contains(tabTitle);
    }

    private Set<Integer> getHiddenRowNumbers(String spreadsheetId, String sheetTitle) throws IOException {
        Set<Integer> hiddenRows = new HashSet<>();

        Spreadsheet spreadsheet = sheetsService.spreadsheets()
                .get(spreadsheetId)
                .setRanges(List.of("'" + sheetTitle + "'"))
                .setIncludeGridData(true)
                .setFields("sheets(properties(title),data(startRow,rowMetadata(hiddenByUser,hiddenByFilter)))")
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

                int startIndex = gridData.getStartRow() == null ? 0 : gridData.getStartRow();

                for (int i = 0; i < rowMetadata.size(); i++) {
                    DimensionProperties row = rowMetadata.get(i);

                    boolean hiddenByUser = Boolean.TRUE.equals(row.getHiddenByUser());
                    boolean hiddenByFilter = Boolean.TRUE.equals(row.getHiddenByFilter());

                    if (hiddenByUser || hiddenByFilter) {
                        hiddenRows.add(startIndex + i + 1);
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
    private boolean isProductRow(List<Object> row, String tabTitle) {
        String sku = getCell(row, 0);
        String name = getCell(row, 1);

        if (sku.isBlank() || name.isBlank()) {
            return false;
        }

        if (sku.equalsIgnoreCase("Модель")) {
            return false;
        }

        String availability = getAvailabilityCell(row, tabTitle);

        String price = getPriceCell(row, tabTitle);

        if (availability.isBlank()) {
            return false;
        }

        return hasPrice(price);
    }

    private String getAvailabilityCell(List<Object> row, String tabTitle) {
        if (isBessTab(tabTitle)) {
            return getCell(row, 4); // E column
        }

        return getCell(row, 2); // C column
    }

    private String getPriceCell(List<Object> row, String tabTitle) {
        if (isBessTab(tabTitle)) {
            return getCell(row, 5); // F column
        }

        if (isSolarCableTab(tabTitle)) {
            String priceWithoutVat = getCell(row, 4);

            if (hasPrice(priceWithoutVat)) {
                return priceWithoutVat;
            }

            return getCell(row, 3); // D column
        }

        return getCell(row, 4); // E column
    }

    private boolean isBessTab(String tabTitle) {
        if (tabTitle == null) {
            return false;
        }

        return tabTitle.equalsIgnoreCase("BESS");
    }

    private boolean isSolarCableTab(String tabTitle) {
        if (tabTitle == null) {
            return false;
        }

        return tabTitle.toLowerCase().contains("сонячний кабель");
    }

    private boolean hasPrice(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String cleaned = value.trim().toLowerCase();

        if (cleaned.equals("-")) {
            return false;
        }

        if (cleaned.contains("деталі") || cleaned.contains("менеджер")) {
            return false;
        }

        return cleaned.matches(".*\\d.*");
    }

    private String getCell(List<Object> row, int index) {
        if (row == null || row.size() <= index || row.get(index) == null) {
            return "";
        }

        return row.get(index).toString().trim();
    }

}