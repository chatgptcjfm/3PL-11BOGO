package com.example.integrationprocess.report;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        String sourceFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String filename = sourceFilename.toLowerCase(java.util.Locale.ROOT);
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
                    "python3", script.toString(), temporaryFile.toString(),
                    "--source-filename", sourceFilename)
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
                String message = output.contains("No report date in filename")
                        ? "파일명에서 보고일을 찾지 못했습니다. 파일명에 26.10.12 형식의 날짜를 포함해 주세요."
                        : "엑셀 구조를 읽지 못했습니다. 원본 일일보고 XLSB인지 확인해 주세요.";
                return ResponseEntity.badRequest().body(Map.of("message", message));
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

    @PutMapping("/{date}/resources/{centerName}")
    public ResponseEntity<Map<String, Object>> updateResource(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String centerName,
            @RequestBody ResourceUpdate update,
            @RequestHeader(value = "X-Report-Import-Password", required = false) String suppliedPassword) {
        if (importPassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "서버에 관리자 암호가 설정되지 않았습니다."));
        }
        if (!passwordMatches(suppliedPassword)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "관리자 암호를 확인해 주세요."));
        }
        if (!isValidResourceUpdate(update)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "자원 수치는 0 이상의 유효한 숫자로 입력해 주세요."));
        }

        Integer centerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_report_center WHERE report_date = ? AND center_name = ?",
                Integer.class, date, centerName);
        if (centerCount == null || centerCount == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "선택한 보고일의 센터 정보가 없습니다."));
        }

        jdbcTemplate.update(
                """
                INSERT INTO daily_report_resource
                    (report_date, center_name, base_count, area_pyeong, office_staff, field_staff, forklift_count, hpt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    base_count = VALUES(base_count), area_pyeong = VALUES(area_pyeong),
                    office_staff = VALUES(office_staff), field_staff = VALUES(field_staff),
                    forklift_count = VALUES(forklift_count), hpt_count = VALUES(hpt_count)
                """,
                date, centerName, update.baseCount(), update.areaPyeong(), update.officeStaff(),
                update.fieldStaff(), update.forkliftCount(), update.hptCount());
        return ResponseEntity.ok(Map.of("message", "자원현황을 저장했습니다."));
    }

    @PutMapping("/{date}/issues/detail-reason")
    public ResponseEntity<Map<String, Object>> updateIssueDetailReason(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody IssueDetailReasonUpdate update,
            @RequestHeader(value = "X-Report-Import-Password", required = false) String suppliedPassword) {
        if (importPassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "서버에 관리자 암호가 설정되지 않았습니다."));
        }
        if (!passwordMatches(suppliedPassword)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "관리자 암호를 확인해 주세요."));
        }
        if (update == null || update.centerName() == null || update.issueType() == null
                || update.causeCode() == null || update.detailReason() == null
                || update.detailReason().length() > 2000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "상세사유를 2,000자 이내로 입력해 주세요."));
        }

        Integer issueCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_report_issue WHERE report_date = ? AND center_name = ? AND issue_type = ? AND cause_code = ?",
                Integer.class, date, update.centerName(), update.issueType(), update.causeCode());
        if (issueCount == null || issueCount == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "선택한 보고일의 이슈 항목을 찾을 수 없습니다."));
        }

        String detailReason = update.detailReason().trim();
        jdbcTemplate.update(
                "UPDATE daily_report_issue SET detail_reason = ? WHERE report_date = ? AND center_name = ? AND issue_type = ? AND cause_code = ?",
                detailReason.isEmpty() ? null : detailReason, date, update.centerName(), update.issueType(), update.causeCode());
        return ResponseEntity.ok(Map.of("message", "상세사유를 저장했습니다."));
    }

    private boolean isValidResourceUpdate(ResourceUpdate update) {
        if (update == null || update.baseCount() == null || update.areaPyeong() == null
                || update.officeStaff() == null || update.fieldStaff() == null
                || update.forkliftCount() == null || update.hptCount() == null) {
            return false;
        }
        int maximumCount = 1_000_000;
        BigDecimal area = update.areaPyeong();
        return update.baseCount() >= 0 && update.baseCount() <= maximumCount
                && update.officeStaff() >= 0 && update.officeStaff() <= maximumCount
                && update.fieldStaff() >= 0 && update.fieldStaff() <= maximumCount
                && update.forkliftCount() >= 0 && update.forkliftCount() <= maximumCount
                && update.hptCount() >= 0 && update.hptCount() <= maximumCount
                && area.compareTo(BigDecimal.ZERO) >= 0
                && area.compareTo(new BigDecimal("10000000")) <= 0
                && area.scale() <= 2;
    }

    public record IssueDetailReasonUpdate(
            String centerName,
            String issueType,
            String causeCode,
            String detailReason) {}

    public record ResourceUpdate(
            Integer baseCount,
            BigDecimal areaPyeong,
            Integer officeStaff,
            Integer fieldStaff,
            Integer forkliftCount,
            Integer hptCount) {}

    private boolean passwordMatches(String suppliedPassword) {
        if (suppliedPassword == null || suppliedPassword.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                importPassword.getBytes(StandardCharsets.UTF_8),
                suppliedPassword.getBytes(StandardCharsets.UTF_8));
    }
}
