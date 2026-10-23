package com.example.integrationprocess.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/reports")
public class DailyReportController {
    private final JdbcTemplate jdbcTemplate;

    public DailyReportController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/dates")
    public List<String> reportDates() {
        return jdbcTemplate.query(
                "SELECT DATE_FORMAT(report_date, '%Y-%m-%d') FROM daily_report_summary ORDER BY report_date",
                (resultSet, rowNumber) -> resultSet.getString(1));
    }

    @GetMapping("/{date}")
    public DailyReport reportForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Summary summary;
        try {
            summary = jdbcTemplate.queryForObject(
                    """
                    SELECT order_boxes, delivered_boxes, shortage_boxes, delivery_rate, unshipped_boxes
                    FROM daily_report_summary
                    WHERE report_date = ?
                    """,
                    (resultSet, rowNumber) -> new Summary(
                            resultSet.getInt("order_boxes"),
                            resultSet.getInt("delivered_boxes"),
                            resultSet.getInt("shortage_boxes"),
                            resultSet.getBigDecimal("delivery_rate"),
                            resultSet.getInt("unshipped_boxes")),
                    date);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(NOT_FOUND, "해당 날짜의 보고 데이터가 없습니다.");
        }

        List<Center> centers = jdbcTemplate.query(
                """
                SELECT center_name, order_boxes, delivered_boxes, shortage_boxes, delivery_rate, unshipped_boxes
                FROM daily_report_center
                WHERE report_date = ?
                ORDER BY center_name
                """,
                (resultSet, rowNumber) -> new Center(
                        resultSet.getString("center_name"),
                        resultSet.getInt("order_boxes"),
                        resultSet.getInt("delivered_boxes"),
                        resultSet.getInt("shortage_boxes"),
                        resultSet.getBigDecimal("delivery_rate"),
                        resultSet.getInt("unshipped_boxes")),
                date);

        List<Issue> issues = jdbcTemplate.query(
                """
                SELECT center_name, issue_type, cause_code, issue_count, box_count, detail_reason
                FROM daily_report_issue
                WHERE report_date = ?
                ORDER BY box_count DESC, center_name, cause_code
                """,
                (resultSet, rowNumber) -> new Issue(
                        resultSet.getString("center_name"),
                        resultSet.getString("issue_type"),
                        resultSet.getString("cause_code"),
                        resultSet.getInt("issue_count"),
                        resultSet.getInt("box_count"),
                        resultSet.getString("detail_reason")),
                date);

        List<Resource> resources = jdbcTemplate.query(
                """
                SELECT center_name, row_label, base_count, area_pyeong, office_staff, field_staff,
                       forklift_count, hpt_count, remarks
                FROM daily_report_resource
                WHERE report_date = ?
                ORDER BY center_name
                """,
                (resultSet, rowNumber) -> new Resource(
                        resultSet.getString("center_name"),
                        resultSet.getString("row_label"),
                        resultSet.getInt("base_count"),
                        resultSet.getBigDecimal("area_pyeong"),
                        resultSet.getInt("office_staff"),
                        resultSet.getInt("field_staff"),
                        resultSet.getInt("forklift_count"),
                        resultSet.getInt("hpt_count"),
                        resultSet.getString("remarks")),
                date);

        List<ShortageDetail> shortageDetails = jdbcTemplate.query(
                """
                SELECT event_date, center_name, issue_type, customer_name, shortage_boxes, process_result, remarks
                FROM daily_report_shortage_detail
                WHERE report_date = ?
                ORDER BY event_date, center_name, source_row
                """,
                (resultSet, rowNumber) -> {
                    java.sql.Date eventDate = resultSet.getDate("event_date");
                    return new ShortageDetail(
                            eventDate == null ? null : eventDate.toLocalDate(),
                            resultSet.getString("center_name"),
                            resultSet.getString("issue_type"),
                            resultSet.getString("customer_name"),
                            resultSet.getBigDecimal("shortage_boxes"),
                            resultSet.getString("process_result"),
                            resultSet.getString("remarks"));
                },
                date);

        return new DailyReport(date, summary, centers, issues, resources, shortageDetails);
    }

    public record DailyReport(
            LocalDate reportDate,
            Summary summary,
            List<Center> centers,
            List<Issue> issues,
            List<Resource> resources,
            List<ShortageDetail> shortageDetails) {}

    public record Summary(
            int orderBoxes,
            int deliveredBoxes,
            int shortageBoxes,
            BigDecimal deliveryRate,
            int unshippedBoxes) {}

    public record Center(
            String centerName,
            int orderBoxes,
            int deliveredBoxes,
            int shortageBoxes,
            BigDecimal deliveryRate,
            int unshippedBoxes) {}

    public record Issue(String centerName, String issueType, String causeCode, int issueCount, int boxCount, String detailReason) {}

    public record Resource(
            String centerName,
            String rowLabel,
            int baseCount,
            BigDecimal areaPyeong,
            int officeStaff,
            int fieldStaff,
            int forkliftCount,
            int hptCount,
            String remarks) {}

    public record ShortageDetail(
            LocalDate eventDate,
            String centerName,
            String issueType,
            String customerName,
            BigDecimal shortageBoxes,
            String processResult,
            String remarks) {}
}
