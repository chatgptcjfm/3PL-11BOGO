package com.example.integrationprocess.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExcelServiceTest {
    private final ExcelService excelService = new ExcelService();

    @Test
    void writesAndReadsAnOoxmlWorkbook() throws Exception {
        byte[] workbookBytes = excelService.createSmokeTestWorkbook();
        Map<String, Object> analysis = excelService.analyze(new ByteArrayInputStream(workbookBytes));

        assertThat(analysis.get("format")).isEqualTo("OOXML/XLSX");
        assertThat(analysis.get("sheetCount")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sheets = (List<Map<String, Object>>) analysis.get("sheets");
        assertThat(sheets).hasSize(1);
        assertThat(sheets.get(0).get("name")).isEqualTo("Smoke Test");
        assertThat(sheets.get(0).get("nonEmptyRows")).isEqualTo(3);
    }
}
