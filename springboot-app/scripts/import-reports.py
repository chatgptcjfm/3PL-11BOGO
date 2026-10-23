#!/usr/bin/env python3
"""Import anonymized daily KPIs and issue aggregates from 3PL XLSB reports."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import tempfile
from collections import Counter
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path

from pyxlsb import open_workbook

APP_DIR = Path(__file__).resolve().parents[1]
ENV_FILE = APP_DIR / ".env"
CENTER_BY_PREFIX = {
    "1": "수도권센터",
    "2": "경남센터",
    "3": "경북센터",
    "4": "전라센터",
    "5": "제주센터",
}
ALLOWED_CAUSES = {
    "공장재고부족",
    "공장재고이동누락",
    "납품시간초과",
    "납품조건미달",
    "미상차",
    "발주서오류",
    "센터재고부족",
    "오상차",
    "용적초과",
    "유통기한",
    "전산오류",
    "제품파손",
}


def read_env() -> dict[str, str]:
    if not ENV_FILE.exists():
        raise SystemExit("Missing springboot-app/.env. Run scripts/create-dev-env.sh first.")
    values: dict[str, str] = {}
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("\"'")
    return values


def cell_map(row) -> dict[int, object]:
    return {cell.c + 1: cell.v for cell in row if cell.v not in (None, "")}


def to_date(value: object, default_year: int | None = None) -> date | None:
    if isinstance(value, (int, float)):
        return (datetime(1899, 12, 30) + timedelta(days=float(value))).date()
    if isinstance(value, str):
        text = value.strip()
        for pattern in ("%Y-%m-%d", "%Y/%m/%d", "%Y.%m.%d"):
            try:
                return datetime.strptime(text, pattern).date()
            except ValueError:
                pass
        if default_year is not None:
            for pattern in ("%m/%d", "%m-%d", "%m.%d"):
                try:
                    parsed = datetime.strptime(text, pattern)
                    return date(default_year, parsed.month, parsed.day)
                except ValueError:
                    pass
    return None


def report_date_from_filename(filename: str) -> date | None:
    basename = re.split(r"[\\/]", filename)[-1]
    patterns = (
        re.compile(r"(?<!\d)(?P<year>\d{2}|\d{4})[._/-](?P<month>\d{1,2})[._/-](?P<day>\d{1,2})(?!\d)"),
        re.compile(r"(?<!\d)(?P<year>\d{2}|\d{4})년\s*(?P<month>\d{1,2})월\s*(?P<day>\d{1,2})일?(?!\d)"),
    )
    for pattern in patterns:
        match = pattern.search(basename)
        if not match:
            continue
        year = int(match.group("year"))
        if year < 100:
            year += 2000
        try:
            return date(year, int(match.group("month")), int(match.group("day")))
        except ValueError as error:
            raise ValueError(f"Invalid report date in filename: {basename}") from error
    return None


def as_int(value: object) -> int:
    if value in (None, ""):
        return 0
    return int(round(float(value)))


def sql_text(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''").replace("\x00", "") + "'"


def sql_num(value: int | float) -> str:
    return str(int(value))


def sql_rate(value: object) -> str:
    try:
        return f"{float(value):.6f}"
    except (TypeError, ValueError):
        return "0"


def as_decimal(value: object) -> Decimal:
    if value in (None, ""):
        return Decimal("0")
    try:
        return Decimal(str(value)).quantize(Decimal("0.01"))
    except (InvalidOperation, ValueError):
        return Decimal("0")


def sql_decimal(value: object) -> str:
    return format(as_decimal(value), "f")


def sql_nullable_text(value: object) -> str:
    if value in (None, ""):
        return "NULL"
    return sql_text(str(value))


def extract_report(
    path: Path, source_filename: str | None = None
) -> tuple[date, dict, list[dict], list[dict], list[dict], list[dict]]:
    report_day = report_date_from_filename(source_filename or path.name)
    if not report_day:
        raise ValueError("No report date in filename; expected a date such as 26.10.12")

    with open_workbook(str(path)) as workbook:
        if not workbook.sheets:
            raise ValueError(f"No sheets in {path.name}")

        with workbook.get_sheet(workbook.sheets[0]) as summary_sheet:
            summary_rows = list(summary_sheet.rows(sparse=True))
        if len(summary_rows) < 34:
            raise ValueError(f"Summary sheet is incomplete: {path.name}")

        workbook_report_day = to_date(cell_map(summary_rows[4]).get(4))
        daily_row = cell_map(summary_rows[14])
        unshipped_row = cell_map(summary_rows[33])
        if not workbook_report_day or daily_row.get(3) != "당일":
            raise ValueError(f"Could not identify workbook report date/KPIs: {path.name}")

        summary = {
            "order_boxes": as_int(daily_row.get(6)),
            "delivered_boxes": as_int(daily_row.get(7)),
            "shortage_boxes": as_int(daily_row.get(8)),
            "delivery_rate": float(daily_row.get(9) or 0),
            "unshipped_boxes": as_int(unshipped_row.get(10)),
        }

        centers = []
        resources = []
        shortage_details = []
        issue_groups: Counter[tuple[str, str, str]] = Counter()
        issue_boxes: Counter[tuple[str, str, str]] = Counter()
        previous_day_unshipped_groups: Counter[tuple[str, str, str]] = Counter()
        previous_day_unshipped_boxes: Counter[tuple[str, str, str]] = Counter()
        current_day_unshipped_boxes: Counter[str] = Counter()
        delivery_adjustment = 0

        for sheet_name in workbook.sheets:
            if sheet_name.startswith("요약"):
                continue
            if "결품현황" in sheet_name:
                prefix = sheet_name.split(".", 1)[0].split("-", 1)[0]
                center_name = CENTER_BY_PREFIX.get(prefix)
                if not center_name:
                    continue
                with workbook.get_sheet(sheet_name) as issue_sheet:
                    issue_rows = list(issue_sheet.rows(sparse=True))
                for source_row, row in enumerate(issue_rows[6:], start=7):
                    cells = cell_map(row)
                    if not any(cells.get(column) not in (None, "") for column in (3, 4, 5, 11, 13, 15)):
                        continue
                    event_day = to_date(cells.get(3), report_day.year)
                    issue_type = str(cells.get(7) or "결품").strip()
                    shortage_details.append({
                        "event_date": event_day,
                        "center_name": center_name,
                        "issue_type": issue_type[:40],
                        "customer_name": str(cells.get(4) or "").strip(),
                        "shortage_boxes": as_decimal(cells.get(5)),
                        "process_result": str(cells.get(11) or "").strip(),
                        "remarks": str(cells.get(13) or "").strip(),
                        "source_sheet": sheet_name,
                        "source_row": source_row,
                    })
                    if issue_type not in {"결품", "미배송"}:
                        issue_type = "기타"
                    cause = str(cells.get(15) or cells.get(14) or cells.get(13) or "기타").strip()
                    if cause not in ALLOWED_CAUSES:
                        cause = "기타"
                    key = (center_name, issue_type[:40], cause[:80])
                    if event_day == report_day - timedelta(days=1) and issue_type == "미배송":
                        previous_day_unshipped_groups[key] += 1
                        previous_day_unshipped_boxes[key] += as_int(cells.get(5))
                    if event_day not in {report_day, report_day + timedelta(days=1)}:
                        continue
                    issue_groups[key] += 1
                    issue_boxes[key] += as_int(cells.get(5))
                    if event_day == report_day and issue_type == "미배송":
                        current_day_unshipped_boxes[center_name] += as_int(cells.get(5))
                continue

            prefix = sheet_name.split(".", 1)[0].split("-", 1)[0]
            # The second number distinguishes the center's 생활/제지 worksheet.
            suffix = sheet_name.split(".", 1)[0].split("-", 1)[1] if "-" in sheet_name.split(".", 1)[0] else "1"
            if prefix == "2" and suffix == "3":
                center_name = "경남센터(제지)"
            else:
                center_name = {
                    "1": "수도권센터",
                    "2": "경남센터(생활)",
                    "3": "경북센터",
                    "4": "전라센터",
                    "5": "제주센터",
                }.get(prefix)
            if not center_name:
                continue

            with workbook.get_sheet(sheet_name) as center_sheet:
                center_rows = list(center_sheet.rows(sparse=True))
            by_excel_row = {row[0].r + 1: row for row in center_rows if row}
            resource_row = cell_map(by_excel_row.get(11, []))
            resources.append({
                "center_name": center_name,
                "row_label": str(resource_row.get(3) or "내용").strip(),
                "base_count": as_int(resource_row.get(4)),
                "area_pyeong": as_decimal(resource_row.get(5)),
                "office_staff": as_int(resource_row.get(6)),
                "field_staff": as_int(resource_row.get(7)),
                "forklift_count": as_int(resource_row.get(8)),
                "hpt_count": as_int(resource_row.get(9)),
                "remarks": str(resource_row.get(10) or "").strip(),
            })
            daily = cell_map(by_excel_row.get(16, []))
            unshipped = cell_map(by_excel_row.get(35, []))
            if daily.get(3) != "당일":
                continue
            order_boxes = as_int(daily.get(6))
            reported_delivered_boxes = as_int(daily.get(7))
            unshipped_boxes = as_int(unshipped.get(10))
            delivered_boxes = reported_delivered_boxes
            if order_boxes > 0:
                delivered_boxes = min(reported_delivered_boxes, max(0, order_boxes - unshipped_boxes))
            delivery_adjustment += reported_delivered_boxes - delivered_boxes
            centers.append({
                "center_name": center_name,
                "order_boxes": order_boxes,
                "delivered_boxes": delivered_boxes,
                "shortage_boxes": as_int(daily.get(8)),
                "delivery_rate": (
                    delivered_boxes / order_boxes if order_boxes > 0 else float(daily.get(9) or 0)
                ),
                "unshipped_boxes": unshipped_boxes,
            })

    if delivery_adjustment:
        summary["delivered_boxes"] = max(0, summary["delivered_boxes"] - delivery_adjustment)
        if summary["order_boxes"] > 0:
            summary["delivery_rate"] = summary["delivered_boxes"] / summary["order_boxes"]

    for center in centers:
        center_name = center["center_name"]
        carryover_keys = [
            key for key in previous_day_unshipped_groups if key[0] == center_name
        ]
        carryover_boxes = sum(previous_day_unshipped_boxes[key] for key in carryover_keys)
        unaccounted_unshipped = center["unshipped_boxes"] - current_day_unshipped_boxes[center_name]
        if carryover_boxes > 0 and carryover_boxes == unaccounted_unshipped:
            for key in carryover_keys:
                issue_groups[key] += previous_day_unshipped_groups[key]
                issue_boxes[key] += previous_day_unshipped_boxes[key]

    issues = [
        {
            "center_name": center,
            "issue_type": issue_type,
            "cause_code": cause,
            "issue_count": int(count),
            "box_count": int(issue_boxes[(center, issue_type, cause)]),
        }
        for (center, issue_type, cause), count in sorted(issue_groups.items())
    ]
    if not centers:
        raise ValueError(f"No center KPI sheets found: {path.name}")
    return report_day, summary, centers, issues, resources, shortage_details


def build_sql(reports: list[tuple[date, dict, list[dict], list[dict], list[dict], list[dict]]]) -> str:
    statements = ["START TRANSACTION;"]
    for report_day, summary, centers, issues, resources, shortage_details in reports:
        day = sql_text(report_day.isoformat())
        statements.append(
            "INSERT INTO daily_report_summary "
            "(report_date, order_boxes, delivered_boxes, shortage_boxes, delivery_rate, unshipped_boxes) VALUES "
            f"({day}, {sql_num(summary['order_boxes'])}, {sql_num(summary['delivered_boxes'])}, "
            f"{sql_num(summary['shortage_boxes'])}, {sql_rate(summary['delivery_rate'])}, "
            f"{sql_num(summary['unshipped_boxes'])}) "
            "ON DUPLICATE KEY UPDATE order_boxes=VALUES(order_boxes), delivered_boxes=VALUES(delivered_boxes), "
            "shortage_boxes=VALUES(shortage_boxes), delivery_rate=VALUES(delivery_rate), "
            "unshipped_boxes=VALUES(unshipped_boxes);"
        )
        statements.append(f"DELETE FROM daily_report_center WHERE report_date={day};")
        for center in centers:
            statements.append(
                "INSERT INTO daily_report_center "
                "(report_date, center_name, order_boxes, delivered_boxes, shortage_boxes, delivery_rate, unshipped_boxes) VALUES "
                f"({day}, {sql_text(center['center_name'])}, {sql_num(center['order_boxes'])}, "
                f"{sql_num(center['delivered_boxes'])}, {sql_num(center['shortage_boxes'])}, "
                f"{sql_rate(center['delivery_rate'])}, {sql_num(center['unshipped_boxes'])});"
            )
        if issues:
            issue_keys = " OR ".join(
                "(center_name=" + sql_text(issue["center_name"])
                + " AND issue_type=" + sql_text(issue["issue_type"])
                + " AND cause_code=" + sql_text(issue["cause_code"]) + ")"
                for issue in issues
            )
            statements.append(
                f"DELETE FROM daily_report_issue WHERE report_date={day} AND NOT ({issue_keys});"
            )
        else:
            statements.append(f"DELETE FROM daily_report_issue WHERE report_date={day};")
        for issue in issues:
            statements.append(
                "INSERT INTO daily_report_issue "
                "(report_date, center_name, issue_type, cause_code, issue_count, box_count) VALUES "
                f"({day}, {sql_text(issue['center_name'])}, {sql_text(issue['issue_type'])}, "
                f"{sql_text(issue['cause_code'])}, {sql_num(issue['issue_count'])}, {sql_num(issue['box_count'])}) "
                "ON DUPLICATE KEY UPDATE issue_count=VALUES(issue_count), box_count=VALUES(box_count);"
            )

        statements.append(f"DELETE FROM daily_report_resource WHERE report_date={day};")
        for resource in resources:
            statements.append(
                "INSERT INTO daily_report_resource "
                "(report_date, center_name, row_label, base_count, area_pyeong, office_staff, field_staff, forklift_count, hpt_count, remarks) VALUES "
                f"({day}, {sql_text(resource['center_name'])}, {sql_text(resource['row_label'])}, "
                f"{sql_num(resource['base_count'])}, {sql_decimal(resource['area_pyeong'])}, "
                f"{sql_num(resource['office_staff'])}, {sql_num(resource['field_staff'])}, "
                f"{sql_num(resource['forklift_count'])}, {sql_num(resource['hpt_count'])}, "
                f"{sql_nullable_text(resource['remarks'])});"
            )

        statements.append(f"DELETE FROM daily_report_shortage_detail WHERE report_date={day};")
        for detail in shortage_details:
            event_day = sql_text(detail['event_date'].isoformat()) if detail['event_date'] else "NULL"
            statements.append(
                "INSERT INTO daily_report_shortage_detail "
                "(report_date, event_date, center_name, issue_type, customer_name, shortage_boxes, process_result, remarks, source_sheet, source_row) VALUES "
                f"({day}, {event_day}, {sql_text(detail['center_name'])}, {sql_text(detail['issue_type'])}, "
                f"{sql_text(detail['customer_name'])}, {sql_decimal(detail['shortage_boxes'])}, "
                f"{sql_nullable_text(detail['process_result'])}, {sql_nullable_text(detail['remarks'])}, "
                f"{sql_text(detail['source_sheet'])}, {sql_num(detail['source_row'])});"
            )
    statements.append("COMMIT;")
    return "\n".join(statements) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="+", type=Path, help="One or more 3PL daily-report .xlsb files")
    parser.add_argument(
        "--source-filename",
        help="Original uploaded filename to use for the report date (single-file imports only)",
    )
    args = parser.parse_args()
    if args.source_filename and len(args.files) != 1:
        raise SystemExit("--source-filename can only be used with one report file")
    if args.source_filename and not report_date_from_filename(args.source_filename):
        raise SystemExit("No report date in filename; expected a date such as 26.10.12")
    env = read_env()
    if not re.fullmatch(r"[A-Za-z0-9_]+", env.get("DB_NAME", "")):
        raise SystemExit("DB_NAME is missing or invalid in .env")
    if not re.fullmatch(r"[A-Za-z0-9_]+", env.get("DB_USERNAME", "")):
        raise SystemExit("DB_USERNAME is missing or invalid in .env")
    reports = [
        extract_report(path, args.source_filename if len(args.files) == 1 else None)
        for path in args.files
    ]
    sql = build_sql(reports)

    defaults = "[client]\n"
    defaults += f"host={env.get('DB_HOST', '127.0.0.1')}\n"
    defaults += f"port={env.get('DB_PORT', '3306')}\n"
    defaults += f"user={env['DB_USERNAME']}\n"
    defaults += f"password={env['DB_PASSWORD']}\n"
    fd, config_path = tempfile.mkstemp(prefix="3pl-import-", suffix=".cnf")
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as config:
            config.write(defaults)
        subprocess.run(
            ["mariadb", f"--defaults-extra-file={config_path}", env["DB_NAME"]],
            input=sql,
            text=True,
            check=True,
            capture_output=True,
        )
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.stderr.strip() or "MariaDB import failed") from error
    finally:
        try:
            os.unlink(config_path)
        except FileNotFoundError:
            pass

    for report_day, _, centers, issues, resources, shortage_details in reports:
        print(
            f"Imported {report_day.isoformat()}: {len(centers)} centers, {len(resources)} resource rows, "
            f"{len(issues)} issue groups, {len(shortage_details)} shortage detail rows"
        )


if __name__ == "__main__":
    main()
