CREATE TABLE IF NOT EXISTS daily_report_summary (
  report_date DATE NOT NULL PRIMARY KEY,
  order_boxes INT NOT NULL DEFAULT 0,
  delivered_boxes INT NOT NULL DEFAULT 0,
  shortage_boxes INT NOT NULL DEFAULT 0,
  delivery_rate DECIMAL(9, 6) NOT NULL DEFAULT 0,
  unshipped_boxes INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS daily_report_center (
  report_date DATE NOT NULL,
  center_name VARCHAR(100) NOT NULL,
  order_boxes INT NOT NULL DEFAULT 0,
  delivered_boxes INT NOT NULL DEFAULT 0,
  shortage_boxes INT NOT NULL DEFAULT 0,
  delivery_rate DECIMAL(9, 6) NOT NULL DEFAULT 0,
  unshipped_boxes INT NOT NULL DEFAULT 0,
  PRIMARY KEY (report_date, center_name),
  CONSTRAINT fk_daily_report_center_summary
    FOREIGN KEY (report_date) REFERENCES daily_report_summary(report_date)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS daily_report_issue (
  report_date DATE NOT NULL,
  center_name VARCHAR(100) NOT NULL,
  issue_type VARCHAR(40) NOT NULL,
  cause_code VARCHAR(80) NOT NULL,
  issue_count INT NOT NULL DEFAULT 0,
  box_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (report_date, center_name, issue_type, cause_code),
  CONSTRAINT fk_daily_report_issue_summary
    FOREIGN KEY (report_date) REFERENCES daily_report_summary(report_date)
    ON DELETE CASCADE
);
