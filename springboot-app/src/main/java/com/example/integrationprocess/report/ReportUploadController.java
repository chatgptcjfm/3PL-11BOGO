package com.example.integrationprocess.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/reports")
public class ReportUploadController {
    private static final Logger log = LoggerFactory.getLogger(ReportUploadController.class);
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final Duration IMPORT_TIMEOUT = Duration.ofSeconds(45);

    private final JdbcTemplate jdbcTemplate;
    private final String importPassword;
    private final Path appDirectory = Path.of("").toAbsolutePath().normalize();

    public ReportUploadController(
            JdbcTemplate jdbcTemplate,
            @Value("${REPORT_IMPORT_PASSWORD:}") String importPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.importPassword = importPassword;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Report-Import-Password", required = false) String suppliedPassword) {
        if (importPassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "서버에 업로드 암호가 설정되지 않았습니다."));
        }
        if (!passwordMatches(suppliedPassword)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "업로드 암호를 확인해 주세요."));
        }
        if (file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("message", "비어 있지 않은 10MB 이하 파일을 선택해 주세요."));
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsb")) {
            return ResponseEntity.badRequest().body(Map.of("message", ".xlsb 일일보고 파일만 등록할 수 있습니다."));
        }

        Path script = appDirectory.resolve("scripts/import-reports.py");
        if (!Files.isRegularFile(script)) {
            log.error("Report import script is missing from {}", script);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "업로드 처리 구성을 찾을 수 없습니다."));
        }

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("3pl-report-upload-", ".xlsb");
            file.transferTo(temporaryFile);

            Process process = new ProcessBuilder(
                    "python3", script.toString(), temporaryFile.toString())
                    .directory(appDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(IMPORT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(Map.of("message", "파일 처리가 오래 걸려 중단했습니다. 파일을 확인하고 다시 시도해 주세요."));
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                log.warn("XLSB report import failed: {}", output);
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "엑셀 구조를 읽지 못했습니다. 원본 일일보고 XLSB인지 확인해 주세요."));
            }

            List<String> dates = jdbcTemplate.query(
                    "SELECT DATE_FORMAT(report_date, '%Y-%m-%d') FROM daily_report_summary ORDER BY report_date",
                    (resultSet, rowNumber) -> resultSet.getString(1));
            return ResponseEntity.ok(Map.of(
                    "message", "보고 데이터를 등록했습니다.",
                    "importSummary", output,
                    "dates", dates));
        } catch (IOException exception) {
            log.error("Could not process uploaded XLSB report", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "파일 처리 중 서버 오류가 발생했습니다."));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "파일 처리 작업이 중단되었습니다."));
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    log.warn("Could not remove temporary report upload", exception);
                }
            }
        }
    }

    private boolean passwordMatches(String suppliedPassword) {
        if (suppliedPassword == null || suppliedPassword.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                importPassword.getBytes(StandardCharsets.UTF_8),
                suppliedPassword.getBytes(StandardCharsets.UTF_8));
    }
}
