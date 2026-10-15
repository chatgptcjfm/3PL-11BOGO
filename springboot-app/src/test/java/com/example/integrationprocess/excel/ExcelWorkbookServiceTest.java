package com.example.integrationprocess.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExcelWorkbookServiceTest {
    private final ExcelWorkbookService service = new ExcelWorkbookService();

    @Test
    void createsAndReadsOoxmlWorkbook() throws Exception {
        byte[] workbook = service.createSampleWorkbook();
        Map<String, Object> result = service.analyze(new ByteArrayInputStream(workbook));

        assertTrue(workbook.length > 0);
        assertEquals(1, result.get("sheetCount"));
        List<?> sheets = (List<?>) result.get("sheets");
        Map<?, ?> firstSheet = (Map<?, ?>) sheets.get(0);
        assertEquals("공정목록", firstSheet.get("name"));
        assertEquals(3, firstSheet.get("nonEmptyRows"));
    }
}
