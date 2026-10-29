package com.example.integrationprocess.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
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

    @GetMapping("/monthly-summary")
    public MonthlyReport monthlySummary(@RequestParam String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw new ResponseStatusException(BAD_REQUEST, "월은 yyyy-MM 형식이어야 합니다.");
        }
        YearMonth selectedMonth;
        try {
            selectedMonth = YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "올바른 연월을 선택해 주세요.");
        }

        LocalDate fromDate = selectedMonth.atDay(1);
        LocalDate toDate = selectedMonth.plusMonths(1).atDay(1);
        List<MonthlyDay> days = jdbcTemplate.query(
                """
                SELECT s.report_date, COUNT(c.center_name) AS center_count,
                       s.order_boxes, s.delivered_boxes, s.shortage_boxes, s.unshipped_boxes, s.delivery_rate
                FROM daily_report_summary s
                LEFT JOIN daily_report_center c ON c.report_date = s.report_date
                WHERE s.report_date >= ? AND s.report_date < ?
                GROUP BY s.report_date, s.order_boxes, s.delivered_boxes, s.shortage_boxes,
                         s.unshipped_boxes, s.delivery_rate
                ORDER BY s.report_date
                """,
                (resultSet, rowNumber) -> new MonthlyDay(
                        resultSet.getDate("report_date").toLocalDate(),
                        resultSet.getInt("center_count"),
                        resultSet.getInt("order_boxes"),
                        resultSet.getInt("delivered_boxes"),
                        resultSet.getInt("shortage_boxes"),
                        resultSet.getInt("unshipped_boxes"),
                        resultSet.getBigDecimal("delivery_rate")),
                fromDate, toDate);

        int orderBoxes = days.stream().mapToInt(MonthlyDay::orderBoxes).sum();
        int deliveredBoxes = days.stream().mapToInt(MonthlyDay::deliveredBoxes).sum();
        int shortageBoxes = days.stream().mapToInt(MonthlyDay::shortageBoxes).sum();
        int unshippedBoxes = days.stream().mapToInt(MonthlyDay::unshippedBoxes).sum();
        int centerReportCount = days.stream().mapToInt(MonthlyDay::centerCount).sum();
        int completeReportDays = (int) days.stream().filter(day -> day.centerCount() >= 6).count();
        BigDecimal deliveryRate = orderBoxes == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(deliveredBoxes).divide(BigDecimal.valueOf(orderBoxes), 6, RoundingMode.HALF_UP);
        MonthlyTotals totals = new MonthlyTotals(
                days.size(), completeReportDays, centerReportCount, days.size() * 6,
                orderBoxes, deliveredBoxes, shortageBoxes, unshippedBoxes, deliveryRate);

        List<MonthlyCenter> centers = jdbcTemplate.query(
                """
                SELECT center_name, COUNT(*) AS report_days,
                       SUM(order_boxes) AS order_boxes, SUM(delivered_boxes) AS delivered_boxes,
                       SUM(shortage_boxes) AS shortage_boxes, SUM(unshipped_boxes) AS unshipped_boxes
                FROM daily_report_center
                WHERE report_date >= ? AND report_date < ?
                GROUP BY center_name
                ORDER BY center_name
                """,
                (resultSet, rowNumber) -> {
                    int centerOrders = resultSet.getInt("order_boxes");
                    int centerDelivered = resultSet.getInt("delivered_boxes");
                    BigDecimal centerRate = centerOrders == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(centerDelivered).divide(BigDecimal.valueOf(centerOrders), 6, RoundingMode.HALF_UP);
                    return new MonthlyCenter(
                            resultSet.getString("center_name"),
                            resultSet.getInt("report_days"),
                            centerOrders,
                            centerDelivered,
                            resultSet.getInt("shortage_boxes"),
                            resultSet.getInt("unshipped_boxes"),
                            centerRate);
                },
                fromDate, toDate);

        List<MonthlyIssue> topIssues = jdbcTemplate.query(
                """
                SELECT center_name, issue_type, cause_code,
                       SUM(issue_count) AS issue_count, SUM(box_count) AS box_count
                FROM daily_report_issue
                WHERE report_date >= ? AND report_date < ?
                GROUP BY center_name, issue_type, cause_code
                ORDER BY issue_count DESC, box_count DESC, center_name, cause_code
                LIMIT 10
                """,
                (resultSet, rowNumber) -> new MonthlyIssue(
                        resultSet.getString("center_name"),
                        resultSet.getString("issue_type"),
                        resultSet.getString("cause_code"),
                        resultSet.getInt("issue_count"),
                        resultSet.getInt("box_count")),
                fromDate, toDate);

        return new MonthlyReport(selectedMonth.toString(), totals, centers, topIssues, days);
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

    public record MonthlyReport(
            String month,
            MonthlyTotals summary,
            List<MonthlyCenter> centers,
            List<MonthlyIssue> topIssues,
            List<MonthlyDay> days) {}

    public record MonthlyTotals(
            int reportDays,
            int completeReportDays,
            int centerReportCount,
            int expectedCenterReports,
            int orderBoxes,
            int deliveredBoxes,
            int shortageBoxes,
            int unshippedBoxes,
            BigDecimal deliveryRate) {}

    public record MonthlyCenter(
            String centerName,
            int reportDays,
            int orderBoxes,
            int deliveredBoxes,
            int shortageBoxes,
            int unshippedBoxes,
            BigDecimal deliveryRate) {}

    public record MonthlyIssue(
            String centerName,
            String issueType,
            String causeCode,
            int issueCount,
            int boxCount) {}

    public record MonthlyDay(
            LocalDate reportDate,
            int centerCount,
            int orderBoxes,
            int deliveredBoxes,
            int shortageBoxes,
            int unshippedBoxes,
            BigDecimal deliveryRate) {}

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
