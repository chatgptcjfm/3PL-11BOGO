package com.example.integrationprocess.api;

import java.io.IOException;
import java.util.Map;

import com.example.integrationprocess.excel.ExcelWorkbookService;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelWorkbookService excelWorkbookService;

    public ExcelController(ExcelWorkbookService excelWorkbookService) {
        this.excelWorkbookService = excelWorkbookService;
    }

    @GetMapping(value = "/sample", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> downloadSample() throws IOException {
        byte[] contents = excelWorkbookService.createSampleWorkbook();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("process-sample.xlsx").build().toString())
                .body(contents);
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> analyze(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (file.isEmpty() || !(filename.endsWith(".xlsx") || filename.endsWith(".xlsm"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드 파일은 비어 있지 않은 .xlsx 또는 .xlsm이어야 합니다.");
        }

        try {
            Map<String, Object> result = excelWorkbookService.analyze(file.getInputStream());
            result.put("filename", file.getOriginalFilename());
            result.put("sizeBytes", file.getSize());
            return result;
        } catch (InvalidFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 OOXML Excel 파일이 아닙니다.", exception);
        }
    }
}
