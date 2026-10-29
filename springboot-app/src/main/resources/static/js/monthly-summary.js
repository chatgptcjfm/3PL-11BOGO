(() => {
  const monthInput = document.getElementById('month-select');
  const selectedMonthLabel = document.getElementById('selected-month-label');
  const headline = document.getElementById('monthly-headline');
  const coverage = document.getElementById('monthly-coverage');
  const errorMessage = document.getElementById('monthly-error');
  const centerRows = document.getElementById('monthly-center-rows');
  const issueRows = document.getElementById('monthly-issue-rows');
  const dayRows = document.getElementById('monthly-day-rows');
  const numberFormat = new Intl.NumberFormat('ko-KR');
  const percentFormat = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const centerOrder = ['수도권센터', '경남센터(생활)', '경남센터(제지)', '경북센터', '전라센터', '제주센터'];

  function centerUnit(centerName) {
    return centerName === '경남센터(제지)' ? 'KG' : 'BOX';
  }

  function cell(text, className = '') {
    const element = document.createElement('td');
    element.textContent = text;
    if (className) element.className = className;
    return element;
  }

  function showEmptyRow(target, colspan, message) {
    const row = document.createElement('tr');
    const empty = document.createElement('td');
    empty.colSpan = colspan;
    empty.className = 'monthly-empty';
    empty.textContent = message;
    row.append(empty);
    target.replaceChildren(row);
  }

  function showError(message) {
    errorMessage.textContent = message;
    errorMessage.hidden = !message;
  }

  function renderCenters(centers, reportDays) {
    if (!centers.length) {
      showEmptyRow(centerRows, 7, '선택한 달에 센터 실적이 없습니다.');
      return;
    }
    const sortedCenters = [...centers].sort((left, right) => {
      const leftIndex = centerOrder.indexOf(left.centerName);
      const rightIndex = centerOrder.indexOf(right.centerName);
      return (leftIndex < 0 ? centerOrder.length : leftIndex) - (rightIndex < 0 ? centerOrder.length : rightIndex);
    });
    centerRows.replaceChildren(...sortedCenters.map((center) => {
      const row = document.createElement('tr');
      const unit = centerUnit(center.centerName);
      row.append(
        cell(center.centerName, 'monthly-center-name'),
        cell(`${numberFormat.format(center.reportDays)} / ${numberFormat.format(reportDays)}일`),
        cell(`${numberFormat.format(center.orderBoxes)} ${unit}`),
        cell(`${numberFormat.format(center.deliveredBoxes)} ${unit}`),
        cell(`${percentFormat.format(Number(center.deliveryRate || 0) * 100)}%`, 'monthly-rate-cell'),
        cell(`${numberFormat.format(center.shortageBoxes)} ${unit}`),
        cell(`${numberFormat.format(center.unshippedBoxes)} ${unit}`)
      );
      return row;
    }));
  }

  function renderIssues(issues) {
    if (!issues.length) {
      showEmptyRow(issueRows, 5, '선택한 달에 집계된 이슈가 없습니다.');
      return;
    }
    issueRows.replaceChildren(...issues.map((issue) => {
      const row = document.createElement('tr');
      row.append(
        cell(issue.centerName),
        cell(issue.issueType, issue.issueType === '결품' ? 'monthly-issue-stockout' : 'monthly-issue-shipping'),
        cell(issue.causeCode, 'monthly-cause'),
        cell(`${numberFormat.format(issue.issueCount)}건`),
        cell(`${numberFormat.format(issue.boxCount)} ${centerUnit(issue.centerName)}`)
      );
      return row;
    }));
  }

  function renderDays(days) {
    if (!days.length) {
      showEmptyRow(dayRows, 5, '등록된 보고일이 없습니다.');
      return;
    }
    dayRows.replaceChildren(...days.map((day) => {
      const row = document.createElement('tr');
      const date = new Date(`${day.reportDate}T00:00:00`);
      const dateLabel = new Intl.DateTimeFormat('ko-KR', { month: 'numeric', day: 'numeric', weekday: 'short' }).format(date);
      const unit = 'BOX';
      row.append(
        cell(dateLabel, 'monthly-date-cell'),
        cell(`${numberFormat.format(day.centerCount)} / 6`, day.centerCount >= 6 ? 'monthly-coverage-complete' : 'monthly-coverage-partial'),
        cell(`${numberFormat.format(day.orderBoxes)} ${unit}`),
        cell(`${percentFormat.format(Number(day.deliveryRate || 0) * 100)}%`, 'monthly-rate-cell'),
        cell(`${numberFormat.format(day.unshippedBoxes)} ${unit}`)
      );
      return row;
    }));
  }

  function renderReport(report) {
    const summary = report.summary;
    const [year, month] = report.month.split('-').map(Number);
    const monthLabel = `${year}년 ${month}월`;
    selectedMonthLabel.textContent = monthLabel;
    headline.textContent = summary.reportDays
      ? `${monthLabel} 운영 실적을 확인하세요.`
      : `${monthLabel}에 등록된 보고서가 없습니다.`;

    document.getElementById('month-delivery-rate').textContent = percentFormat.format(Number(summary.deliveryRate || 0) * 100);
    document.getElementById('month-order-boxes').textContent = numberFormat.format(summary.orderBoxes || 0);
    document.getElementById('month-shortage-boxes').textContent = numberFormat.format(summary.shortageBoxes || 0);
    document.getElementById('month-unshipped-boxes').textContent = numberFormat.format(summary.unshippedBoxes || 0);

    const expected = Number(summary.expectedCenterReports || 0);
    const received = Number(summary.centerReportCount || 0);
    const completeDays = Number(summary.completeReportDays || 0);
    coverage.textContent = summary.reportDays
      ? `보고일 ${numberFormat.format(summary.reportDays)}일 · 6개 센터 모두 접수된 날 ${numberFormat.format(completeDays)}일 · 센터별 접수 ${numberFormat.format(received)} / ${numberFormat.format(expected)}건. 부분 접수일의 요약은 접수된 센터 기준입니다.`
      : '해당 월에 등록된 일일보고가 없습니다. 월간 수치는 0으로 표시됩니다.';
    document.getElementById('center-report-count').textContent = `센터 접수 ${numberFormat.format(received)} / ${numberFormat.format(expected)}`;

    renderCenters(report.centers || [], summary.reportDays || 0);
    renderIssues(report.topIssues || []);
    renderDays(report.days || []);
  }

  async function loadMonth(month) {
    if (!month) return;
    monthInput.value = month;
    selectedMonthLabel.textContent = month.replace('-', '년 ') + '월';
    headline.textContent = '월간 보고 데이터를 불러오는 중입니다.';
    coverage.textContent = '등록된 보고일과 센터 접수 범위를 확인하고 있습니다.';
    showError('');
    try {
      const response = await fetch(`/api/reports/monthly-summary?month=${encodeURIComponent(month)}`);
      const result = await response.json();
      if (!response.ok) throw new Error(result.message || '월간 보고 데이터를 불러오지 못했습니다.');
      renderReport(result);
    } catch (error) {
      headline.textContent = '월간 요약을 불러오지 못했습니다.';
      coverage.textContent = '잠시 후 다시 시도해 주세요.';
      showError(error.message || '월간 보고 데이터를 조회하는 중 오류가 발생했습니다.');
      showEmptyRow(centerRows, 7, '데이터를 불러오지 못했습니다.');
      showEmptyRow(issueRows, 5, '데이터를 불러오지 못했습니다.');
      showEmptyRow(dayRows, 5, '데이터를 불러오지 못했습니다.');
    }
  }

  monthInput.addEventListener('change', () => loadMonth(monthInput.value));

  const sidebar = document.getElementById('workspace-sidebar');
  const sidebarToggle = document.getElementById('sidebar-toggle');
  const sidebarEdgeToggle = document.getElementById('sidebar-edge-toggle');
  const sidebarEdgeIcon = document.getElementById('sidebar-edge-icon');

  function setSidebarHidden(isHidden, persist = true) {
    document.body.classList.toggle('workspace-sidebar-hidden', isHidden);
    sidebar.toggleAttribute('inert', isHidden);
    sidebar.setAttribute('aria-hidden', String(isHidden));
    const label = isHidden ? '왼쪽 메뉴 표시' : '왼쪽 메뉴 숨기기';
    [sidebarToggle, sidebarEdgeToggle].forEach((toggle) => {
      toggle.setAttribute('aria-expanded', String(!isHidden));
      toggle.setAttribute('aria-label', label);
      toggle.title = label;
    });
    sidebarEdgeIcon.textContent = isHidden ? '›' : '‹';
    if (persist) {
      try {
        localStorage.setItem('3pl-workspace-sidebar-hidden', String(isHidden));
      } catch (_) {
        // Sidebar toggle still works for the current page.
      }
    }
  }

  [sidebarToggle, sidebarEdgeToggle].forEach((toggle) => {
    toggle.addEventListener('click', () => {
      setSidebarHidden(!document.body.classList.contains('workspace-sidebar-hidden'));
    });
  });
  try {
    setSidebarHidden(localStorage.getItem('3pl-workspace-sidebar-hidden') === 'true', false);
  } catch (_) {
    setSidebarHidden(false, false);
  }

  async function initialize() {
    const now = new Date();
    const fallbackMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    try {
      const response = await fetch('/api/reports/dates');
      if (!response.ok) throw new Error('등록된 보고일 목록을 불러오지 못했습니다.');
      const dates = await response.json();
      const latestMonth = dates.length ? dates[dates.length - 1].slice(0, 7) : fallbackMonth;
      await loadMonth(latestMonth);
    } catch (error) {
      monthInput.value = fallbackMonth;
      await loadMonth(fallbackMonth);
      if (!errorMessage.textContent) showError(error.message || '보고일 목록을 불러오지 못했습니다.');
    }
  }

  initialize();
})();
