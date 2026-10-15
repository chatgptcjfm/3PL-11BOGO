package com.example.integrationprocess.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.stereotype.Service;

@Service
public class ExcelWorkbookService {
    private static final List<String> SAMPLE_HEADERS = List.of("공정", "상태", "담당자");
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public byte[] createSampleWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("공정목록");
            Row header = sheet.createRow(0);
            for (int column = 0; column < SAMPLE_HEADERS.size(); column++) {
                header.createCell(column).setCellValue(SAMPLE_HEADERS.get(column));
            }

            addSampleRow(sheet, 1, "수주", "준비", "담당자 A");
            addSampleRow(sheet, 2, "생산", "대기", "담당자 B");
            for (int column = 0; column < SAMPLE_HEADERS.size(); column++) {
                sheet.autoSizeColumn(column);
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    public Map<String, Object> analyze(InputStream input) throws IOException, InvalidFormatException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            List<Map<String, Object>> sheets = new ArrayList<>();
            for (Sheet sheet : workbook) {
                int nonEmptyRows = 0;
                List<List<String>> previewRows = new ArrayList<>();

                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    boolean hasValue = false;
                    for (int column = 0; column < row.getLastCellNum(); column++) {
                        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String value = cell == null ? "" : formatter.formatCellValue(cell);
                        values.add(value);
                        hasValue |= !value.isBlank();
                    }
                    if (hasValue) {
                        nonEmptyRows++;
                        if (previewRows.size() < 10) {
                            previewRows.add(values);
                        }
                    }
                }

                Map<String, Object> sheetSummary = new LinkedHashMap<>();
                sheetSummary.put("name", sheet.getSheetName());
                sheetSummary.put("lastRowNumber", sheet.getLastRowNum() + 1);
                sheetSummary.put("nonEmptyRows", nonEmptyRows);
                sheetSummary.put("previewRows", previewRows);
                sheets.add(sheetSummary);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("format", workbook.getClass().getSimpleName());
            result.put("sheetCount", workbook.getNumberOfSheets());
            result.put("sheets", sheets);
            return result;
        }
    }

    private void addSampleRow(Sheet sheet, int rowIndex, String process, String status, String owner) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(process);
        row.createCell(1).setCellValue(status);
        row.createCell(2).setCellValue(owner);
    }
}
