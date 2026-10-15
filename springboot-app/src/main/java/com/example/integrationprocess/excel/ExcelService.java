package com.example.integrationprocess.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelService {
    private static final int MAX_PREVIEW_ROWS = 100;
    private static final int MAX_PREVIEW_COLUMNS = 50;

    public byte[] createSmokeTestWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Smoke Test");
            writeRow(sheet, 0, "Item", "Quantity");
            writeRow(sheet, 1, "Notebook", "2");
            writeRow(sheet, 2, "Folder", "3");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public Map<String, Object> analyze(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            List<Map<String, Object>> sheets = new ArrayList<>();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                List<List<String>> preview = new ArrayList<>();
                int nonEmptyRows = 0;
                int lastRow = sheet.getLastRowNum();

                for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRow; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    List<String> values = new ArrayList<>();
                    boolean nonEmpty = false;
                    int lastCell = Math.min(row.getLastCellNum(), MAX_PREVIEW_COLUMNS);
                    for (int columnIndex = 0; columnIndex < lastCell; columnIndex++) {
                        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String value = cell == null ? "" : formatter.formatCellValue(cell);
                        values.add(value);
                        nonEmpty |= !value.isBlank();
                    }
                    if (nonEmpty) {
                        nonEmptyRows++;
                        if (preview.size() < MAX_PREVIEW_ROWS) {
                            preview.add(values);
                        }
                    }
                }

                Map<String, Object> sheetResult = new LinkedHashMap<>();
                sheetResult.put("name", sheet.getSheetName());
                sheetResult.put("nonEmptyRows", nonEmptyRows);
                sheetResult.put("preview", preview);
                sheets.add(sheetResult);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("format", "OOXML/XLSX");
            result.put("sheetCount", workbook.getNumberOfSheets());
            result.put("sheets", sheets);
            return result;
        }
    }

    private void writeRow(Sheet sheet, int rowIndex, String first, String second) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(first);
        row.createCell(1).setCellValue(second);
    }
}
