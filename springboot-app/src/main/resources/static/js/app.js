(() => {
  const reportDateInput = document.getElementById('report-date');
  const reportDateLabel = document.getElementById('today-label');
  const centerReportDate = document.getElementById('center-report-date');
  const resourceReportDate = document.getElementById('resource-report-date');
  const centerList = document.getElementById('center-list');
  const issueRows = document.getElementById('issue-rows');
  const searchInput = document.getElementById('issue-search');
  const filterButtons = [...document.querySelectorAll('.filter-tab')];
  const dashboardTabs = [...document.querySelectorAll('.dashboard-tab')];
  const centerTabs = [...document.querySelectorAll('.center-tab')];
  const toast = document.getElementById('toast');
  const uploadOverlay = document.getElementById('report-upload-overlay');
  const uploadForm = document.getElementById('report-upload-form');
  const uploadFileInput = document.getElementById('report-upload-file');
  const uploadFileName = document.getElementById('report-upload-filename');
  const uploadPasswordInput = document.getElementById('report-upload-password');
  const uploadStatus = document.getElementById('report-upload-status');
  const uploadSubmitButton = document.getElementById('submit-report-upload');
  const causeDetailOverlay = document.getElementById('cause-detail-overlay');
  let activeFilter = 'all';
  let currentReport = null;
  let currentCenters = [];
  let activeCenterName = '수도권센터';
  let toastTimer;
  let causeDialogTrigger = null;

  const numberFormat = new Intl.NumberFormat('ko-KR');
  const percentFormat = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const centerStyles = {
    '수도권센터': 'avatar-blue',
    '경남센터(생활)': 'avatar-purple',
    '경남센터(제지)': 'avatar-pink',
    '경북센터': 'avatar-orange',
    '전라센터': 'avatar-green',
    '제주센터': 'avatar-cyan'
  };
  const centerOrder = [
    '수도권센터',
    '경남센터(생활)',
    '경남센터(제지)',
    '경북센터',
    '전라센터',
    '제주센터'
  ];
  function centerUnit(centerName) {
    return centerName === '경남센터(제지)' ? 'KG' : 'BOX';
  }

  function formatDate(value) {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric', month: 'long', day: 'numeric', weekday: 'short'
    }).format(new Date(year, month - 1, day));
  }

  function populateReportDates(dates) {
    const options = dates.map((date) => {
      const option = document.createElement('option');
      option.value = date;
      option.textContent = formatDate(date);
      return option;
    });
    reportDateInput.replaceChildren(...options);
  }

  function formatCauseName(causeName) {
    return causeName || '미분류';
  }

  function showCauseDetails(issue, trigger) {
    document.getElementById('cause-detail-title').textContent = formatCauseName(issue.causeCode);
    document.getElementById('cause-detail-name').textContent = formatCauseName(issue.causeCode);
    document.getElementById('cause-detail-date').textContent = currentReport?.reportDate || '—';
    document.getElementById('cause-detail-center').textContent = issue.centerName;
    document.getElementById('cause-detail-type').textContent = issue.issueType;
    document.getElementById('cause-detail-count').textContent = `${numberFormat.format(issue.issueCount)}건`;
    document.getElementById('cause-detail-boxes').textContent = `${numberFormat.format(issue.boxCount)} ${centerUnit(issue.centerName)}`;
    causeDialogTrigger = trigger;
    document.getElementById('cause-detail-overlay').hidden = false;
    document.body.classList.add('cause-detail-open');
    document.getElementById('close-cause-detail').focus();
  }

  function closeCauseDetails() {
    const overlay = document.getElementById('cause-detail-overlay');
    if (overlay.hidden) return;
    overlay.hidden = true;
    document.body.classList.remove('cause-detail-open');
    if (causeDialogTrigger?.isConnected) causeDialogTrigger.focus();
    causeDialogTrigger = null;
  }

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.remove('is-visible'), 3200);
  }

  function setMetric(id, value) {
    document.getElementById(id).textContent = value;
  }

  function setUnavailable(message) {
    currentReport = null;
    setMetric('delivery-rate', '—');
    setMetric('order-boxes', '—');
    setMetric('shortage-boxes', '—');
    setMetric('unshipped-boxes', '—');
    document.getElementById('delivery-progress').style.width = '0%';
    document.getElementById('delivery-rate-status').textContent = '보고 없음';
    document.getElementById('issue-total').textContent = '0';
    document.getElementById('nav-issue-count').textContent = '0';
    document.querySelector('[data-filter="all"] span').textContent = '0';
    document.querySelector('[data-filter="결품"] span').textContent = '0';
    document.querySelector('[data-filter="미배송"] span').textContent = '0';
    document.querySelector('[data-filter="기타"] span').textContent = '0';
    document.getElementById('data-warning').hidden = true;
    document.getElementById('visible-count').textContent = message;
    renderCenters([]);
    issueRows.replaceChildren();
    const currentDate = reportDateInput.value ? formatDate(reportDateInput.value) : '—';
    centerReportDate.textContent = currentDate;
    resourceReportDate.textContent = currentDate;
  }

  function updateKpis(summary) {
    const deliveryRate = Number(summary.deliveryRate || 0) * 100;
    setMetric('delivery-rate', percentFormat.format(deliveryRate));
    setMetric('order-boxes', numberFormat.format(summary.orderBoxes || 0));
    setMetric('shortage-boxes', numberFormat.format(summary.shortageBoxes || 0));
    setMetric('unshipped-boxes', numberFormat.format(summary.unshippedBoxes || 0));
    document.getElementById('delivery-progress').style.width = `${Math.min(100, Math.max(0, deliveryRate))}%`;
    document.getElementById('delivery-rate-status').textContent = deliveryRate >= 98 ? '목표 달성' : '확인 필요';
  }

  function renderActiveCenter() {
    const center = currentCenters.find((item) => item.centerName === activeCenterName);
    const activeTab = centerTabs.find((tab) => tab.dataset.centerName === activeCenterName);
    const panel = document.getElementById('active-center-panel');
    const avatar = document.getElementById('active-center-avatar');
    const centerName = document.getElementById('active-center-name');
    const statusLine = document.getElementById('center-status-line');
    panel.setAttribute('aria-labelledby', activeTab?.id || centerTabs[0].id);
    centerName.textContent = activeCenterName;
    avatar.className = `center-avatar ${centerStyles[activeCenterName] || 'avatar-blue'}`;
    avatar.textContent = activeCenterName.slice(0, 1);
    const unit = centerUnit(activeCenterName);
    ['center-order-unit', 'center-delivered-unit', 'center-shortage-unit', 'center-unshipped-unit']
      .forEach((id) => setMetric(id, unit));

    if (!center) {
      statusLine.textContent = '선택한 보고일의 센터 데이터가 없습니다.';
      setMetric('center-order-boxes', '—');
      setMetric('center-delivered-boxes', '—');
      setMetric('center-shortage-boxes', '—');
      setMetric('center-unshipped-boxes', '—');
      setMetric('center-delivery-rate', '—');
      document.getElementById('center-delivery-progress').style.width = '0%';
      return;
    }

    const deliveryRate = Math.min(100, Math.max(0, Number(center.deliveryRate || 0) * 100));
    statusLine.textContent = `${numberFormat.format(center.deliveredBoxes)} / ${numberFormat.format(center.orderBoxes)} ${unit} 출고 완료`;
    setMetric('center-order-boxes', numberFormat.format(center.orderBoxes));
    setMetric('center-delivered-boxes', numberFormat.format(center.deliveredBoxes));
    setMetric('center-shortage-boxes', numberFormat.format(center.shortageBoxes));
    setMetric('center-unshipped-boxes', numberFormat.format(center.unshippedBoxes));
    setMetric('center-delivery-rate', `${percentFormat.format(deliveryRate)}%`);
    const progress = document.getElementById('center-delivery-progress');
    progress.style.width = `${deliveryRate}%`;
    progress.style.background = deliveryRate < 98 ? '#e67570' : deliveryRate < 99.5 ? '#dbad5b' : '#5e9c7c';
  }

  function selectCenter(centerName) {
    activeCenterName = centerName;
    centerTabs.forEach((tab) => {
      const isActive = tab.dataset.centerName === activeCenterName;
      tab.setAttribute('aria-selected', String(isActive));
      tab.tabIndex = isActive ? 0 : -1;
    });
    renderActiveCenter();
  }

  function renderCenterStatus() {
    centerList.replaceChildren();
    currentCenters.forEach((center) => {
      const row = document.createElement('article');
      row.className = 'center-row';
      const nameGroup = document.createElement('div');
      nameGroup.className = 'center-name';
      const avatar = document.createElement('span');
      avatar.className = `center-avatar ${centerStyles[center.centerName] || 'avatar-blue'}`;
      avatar.textContent = center.centerName.slice(0, 1);
      const name = document.createElement('span');
      const label = document.createElement('strong');
      label.textContent = center.centerName;
      const detail = document.createElement('small');
      const unit = centerUnit(center.centerName);
      detail.textContent = `${numberFormat.format(center.deliveredBoxes)} / ${numberFormat.format(center.orderBoxes)} ${unit} 출고`;
      name.append(label, detail);
      nameGroup.append(avatar, name);

      const metrics = document.createElement('div');
      metrics.className = 'center-metrics';
      const track = document.createElement('div');
      track.className = 'center-bar';
      const fill = document.createElement('span');
      const rate = Math.min(100, Math.max(0, Number(center.deliveryRate || 0) * 100));
      fill.style.width = `${rate}%`;
      fill.style.background = rate < 98 ? '#e67570' : rate < 99.5 ? '#dbad5b' : '#5e9c7c';
      track.append(fill);
      const foot = document.createElement('small');
      const rateLabel = document.createElement('b');
      rateLabel.textContent = `${percentFormat.format(rate)}%`;
      const unshipped = document.createElement('i');
      unshipped.className = `center-issue-count ${center.unshippedBoxes > 0 ? 'issue-count-red' : 'issue-count-green'}`;
      unshipped.textContent = `미배송 ${numberFormat.format(center.unshippedBoxes)} ${unit}`;
      foot.append(rateLabel, unshipped);
      metrics.append(track, foot);
      row.append(nameGroup, metrics);
      centerList.append(row);
    });
    if (!currentCenters.length) {
      const emptyState = document.createElement('p');
      emptyState.className = 'center-empty-state';
      emptyState.textContent = '선택한 보고일의 센터 데이터가 없습니다.';
      centerList.append(emptyState);
    }
  }

  function renderCenters(centers) {
    currentCenters = [...centers].sort((left, right) => {
      const leftIndex = centerOrder.indexOf(left.centerName);
      const rightIndex = centerOrder.indexOf(right.centerName);
      return (leftIndex < 0 ? centerOrder.length : leftIndex)
        - (rightIndex < 0 ? centerOrder.length : rightIndex);
    });
    renderCenterStatus();
    if (currentCenters.length && !currentCenters.some((center) => center.centerName === activeCenterName)) {
      activeCenterName = centerOrder.find((name) => currentCenters.some((center) => center.centerName === name))
        || currentCenters[0].centerName;
    }
    selectCenter(activeCenterName);
    document.getElementById('connected-center-count').textContent = currentCenters.length
      ? `${currentCenters.length}개 센터`
      : '보고 없음';
  }

  function renderIssues(issues) {
    const term = searchInput.value.trim().toLocaleLowerCase('ko-KR');
    const matching = issues.filter((issue) => {
      const typeMatches = activeFilter === 'all' || issue.issueType === activeFilter;
      const causeName = formatCauseName(issue.causeCode);
      const searchMatches = !term || `${issue.centerName} ${causeName} ${issue.issueType}`
        .toLocaleLowerCase('ko-KR').includes(term);
      return typeMatches && searchMatches;
    });
    issueRows.replaceChildren();

    matching.forEach((issue) => {
      const row = document.createElement('tr');
      row.dataset.type = issue.issueType;
      const centerCell = document.createElement('td');
      centerCell.textContent = issue.centerName;

      const typeCell = document.createElement('td');
      const type = document.createElement('span');
      type.className = `type-pill ${issue.issueType === '결품' ? 'type-stockout' : 'type-delivery'}`;
      const dot = document.createElement('i');
      type.append(dot, document.createTextNode(issue.issueType));
      typeCell.append(type);

      const countCell = document.createElement('td');
      countCell.textContent = `${numberFormat.format(issue.issueCount)}건`;
      const causeCell = document.createElement('td');
      const causeDisplay = document.createElement('div');
      causeDisplay.className = 'cause-display';
      const causeName = document.createElement('span');
      causeName.className = 'cause-name';
      causeName.textContent = formatCauseName(issue.causeCode);
      const causeButton = document.createElement('button');
      causeButton.className = 'cause-detail-button';
      causeButton.type = 'button';
      causeButton.textContent = '상세';
      causeButton.setAttribute('aria-label', `${causeName.textContent} 상세 보기`);
      causeButton.addEventListener('click', () => showCauseDetails(issue, causeButton));
      causeDisplay.append(causeName, causeButton);
      causeCell.append(causeDisplay);
      const boxesCell = document.createElement('td');
      boxesCell.textContent = `${numberFormat.format(issue.boxCount)} ${centerUnit(issue.centerName)}`;
      row.append(centerCell, typeCell, countCell, causeCell, boxesCell);
      issueRows.append(row);
    });

    const totalIssues = issues.reduce((total, issue) => total + issue.issueCount, 0);
    const shortageCount = issues.filter((issue) => issue.issueType === '결품')
      .reduce((total, issue) => total + issue.issueCount, 0);
    const unshippedCount = issues.filter((issue) => issue.issueType === '미배송')
      .reduce((total, issue) => total + issue.issueCount, 0);
    const otherCount = issues.filter((issue) => issue.issueType === '기타')
      .reduce((total, issue) => total + issue.issueCount, 0);
    document.getElementById('issue-total').textContent = numberFormat.format(totalIssues);
    document.getElementById('nav-issue-count').textContent = numberFormat.format(totalIssues);
    document.querySelector('[data-filter="all"] span').textContent = numberFormat.format(totalIssues);
    document.querySelector('[data-filter="결품"] span').textContent = numberFormat.format(shortageCount);
    document.querySelector('[data-filter="미배송"] span').textContent = numberFormat.format(unshippedCount);
    document.querySelector('[data-filter="기타"] span').textContent = numberFormat.format(otherCount);
    document.getElementById('visible-count').textContent = `${matching.length}개 집계 항목 표시 · 거래처·담당자 정보 제외`;

    const detailedUnshippedBoxes = issues.filter((issue) => issue.issueType === '미배송' && centerUnit(issue.centerName) === 'BOX')
      .reduce((total, issue) => total + issue.boxCount, 0);
    const reportedUnshippedBoxes = Number(currentReport?.summary?.unshippedBoxes || 0);
    const dataWarning = document.getElementById('data-warning');
    dataWarning.hidden = detailedUnshippedBoxes === reportedUnshippedBoxes;
    dataWarning.textContent = dataWarning.hidden ? ''
      : `원본 요약 미배송량(${numberFormat.format(reportedUnshippedBoxes)} BOX)과 BOX 단위 상세 이슈 합계(${numberFormat.format(detailedUnshippedBoxes)} BOX)가 다릅니다. 상세 기록 누락 또는 집계 기준 차이를 확인해 주세요.`;
  }

  async function loadReport(dateValue) {
    reportDateInput.value = dateValue;
    reportDateLabel.textContent = formatDate(dateValue);
    centerReportDate.textContent = formatDate(dateValue);
    resourceReportDate.textContent = formatDate(dateValue);
    try {
      window.localStorage.setItem('3pl-report-date', dateValue);
    } catch (_) {
      // The selected date still works if storage is unavailable.
    }

    try {
      const response = await fetch(`/api/reports/${encodeURIComponent(dateValue)}`);
      if (response.status === 404) {
        setUnavailable('선택한 날짜의 보고 데이터가 없습니다. 등록된 날짜를 선택해 주세요.');
        return;
      }
      if (!response.ok) throw new Error(`보고서 조회 실패 (${response.status})`);
      const report = await response.json();
      currentReport = report;
      updateKpis(report.summary);
      renderCenters(report.centers || []);
      renderIssues(report.issues || []);
    } catch (error) {
      setUnavailable('보고 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      showToast(error.message || '보고 데이터 조회 중 오류가 발생했습니다.');
    }
  }

  function exportReport() {
    if (!currentReport) {
      showToast('내보낼 보고 데이터가 없습니다.');
      return;
    }
    const { reportDate, summary, centers, issues } = currentReport;
    const rows = [
      ['보고일', reportDate],
      ['주문량(BOX)', summary.orderBoxes],
      ['납품량(BOX)', summary.deliveredBoxes],
      ['결품량(BOX)', summary.shortageBoxes],
      ['납품율(%)', percentFormat.format(Number(summary.deliveryRate) * 100)],
      ['미배송량(BOX)', summary.unshippedBoxes],
      [],
      ['센터', '단위', '주문량', '납품량', '결품량', '납품율(%)', '미배송량'],
      ...centers.map((center) => [
        center.centerName, centerUnit(center.centerName), center.orderBoxes, center.deliveredBoxes, center.shortageBoxes,
        percentFormat.format(Number(center.deliveryRate) * 100), center.unshippedBoxes
      ]),
      [],
      ['센터', '유형', '원인', '건수', '수량', '단위'],
      ...issues.map((issue) => [
        issue.centerName, issue.issueType, formatCauseName(issue.causeCode), issue.issueCount,
        issue.boxCount, centerUnit(issue.centerName)
      ])
    ];
    const csv = `\uFEFF${rows.map((row) => row.map((cell) => `"${String(cell ?? '').replaceAll('"', '""')}"`).join(',')).join('\r\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `3pl-report-${reportDate}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  function activateDashboardTab(tab) {
    const activePanelId = tab.getAttribute('aria-controls');
    dashboardTabs.forEach((tabButton) => {
      const isActive = tabButton === tab;
      tabButton.setAttribute('aria-selected', String(isActive));
      tabButton.tabIndex = isActive ? 0 : -1;
    });
    document.querySelectorAll('.content-grid > [role="tabpanel"]').forEach((panel) => {
      panel.hidden = panel.id !== activePanelId;
    });
  }

  dashboardTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => activateDashboardTab(tab));
    tab.addEventListener('keydown', (event) => {
      let nextIndex = index;
      if (event.key === 'ArrowRight') nextIndex = (index + 1) % dashboardTabs.length;
      else if (event.key === 'ArrowLeft') nextIndex = (index - 1 + dashboardTabs.length) % dashboardTabs.length;
      else if (event.key === 'Home') nextIndex = 0;
      else if (event.key === 'End') nextIndex = dashboardTabs.length - 1;
      else return;
      event.preventDefault();
      dashboardTabs[nextIndex].focus();
      activateDashboardTab(dashboardTabs[nextIndex]);
    });
  });
  activateDashboardTab(dashboardTabs[0]);
  document.getElementById('center-status-link').addEventListener('click', (event) => {
    event.preventDefault();
    activateDashboardTab(document.getElementById('tab-centers'));
    document.getElementById('center-status-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  centerTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectCenter(tab.dataset.centerName));
    tab.addEventListener('keydown', (event) => {
      let nextIndex = index;
      if (event.key === 'ArrowRight') nextIndex = (index + 1) % centerTabs.length;
      else if (event.key === 'ArrowLeft') nextIndex = (index - 1 + centerTabs.length) % centerTabs.length;
      else if (event.key === 'Home') nextIndex = 0;
      else if (event.key === 'End') nextIndex = centerTabs.length - 1;
      else return;
      event.preventDefault();
      centerTabs[nextIndex].focus();
      selectCenter(centerTabs[nextIndex].dataset.centerName);
    });
  });

  filterButtons.forEach((button) => {
    button.addEventListener('click', () => {
      activeFilter = button.dataset.filter;
      filterButtons.forEach((item) => item.classList.toggle('is-selected', item === button));
      if (currentReport) renderIssues(currentReport.issues || []);
    });
  });
  searchInput.addEventListener('input', () => {
    if (currentReport) renderIssues(currentReport.issues || []);
  });
  window.addEventListener('keydown', (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      searchInput.focus();
    }
  });
  reportDateInput.addEventListener('change', () => {
    if (reportDateInput.value) loadReport(reportDateInput.value);
  });
  function setUploadStatus(message, state = '') {
    uploadStatus.textContent = message;
    uploadStatus.classList.toggle('is-error', state === 'error');
    uploadStatus.classList.toggle('is-success', state === 'success');
  }

  function openUploadDialog() {
    uploadOverlay.hidden = false;
    document.body.classList.add('upload-open');
    document.getElementById('open-report-upload').setAttribute('aria-expanded', 'true');
    setUploadStatus('업로드 후 보고일 기준으로 누적됩니다. 동일 날짜 파일은 재등록 시 갱신됩니다.');
    window.requestAnimationFrame(() => uploadFileInput.focus());
  }

  function closeUploadDialog() {
    if (uploadOverlay.hidden) return;
    uploadOverlay.hidden = true;
    document.body.classList.remove('upload-open');
    document.getElementById('open-report-upload').setAttribute('aria-expanded', 'false');
    uploadPasswordInput.value = '';
    uploadSubmitButton.disabled = false;
    uploadSubmitButton.textContent = '등록하기';
    setUploadStatus('업로드 후 보고일 기준으로 누적됩니다. 동일 날짜 파일은 재등록 시 갱신됩니다.');
    document.getElementById('open-report-upload').focus();
  }

  document.getElementById('open-report-upload').addEventListener('click', openUploadDialog);
  document.getElementById('close-cause-detail').addEventListener('click', closeCauseDetails);
  document.getElementById('done-cause-detail').addEventListener('click', closeCauseDetails);
  causeDetailOverlay.addEventListener('click', (event) => {
    if (event.target === causeDetailOverlay) closeCauseDetails();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !causeDetailOverlay.hidden) closeCauseDetails();
  });
  document.getElementById('close-report-upload').addEventListener('click', closeUploadDialog);
  document.getElementById('cancel-report-upload').addEventListener('click', closeUploadDialog);
  uploadOverlay.addEventListener('click', (event) => {
    if (event.target === uploadOverlay) closeUploadDialog();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !uploadOverlay.hidden) closeUploadDialog();
  });
  uploadFileInput.addEventListener('change', () => {
    const file = uploadFileInput.files?.[0];
    uploadFileName.textContent = file ? file.name : 'XLSB 파일 선택';
    if (file && file.size > 10 * 1024 * 1024) {
      setUploadStatus('파일은 10MB 이하만 등록할 수 있습니다.', 'error');
    } else {
      setUploadStatus('업로드 후 보고일 기준으로 누적됩니다. 동일 날짜 파일은 재등록 시 갱신됩니다.');
    }
  });
  uploadForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const file = uploadFileInput.files?.[0];
    if (!file) {
      setUploadStatus('먼저 XLSB 파일을 선택해 주세요.', 'error');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setUploadStatus('파일은 10MB 이하만 등록할 수 있습니다.', 'error');
      return;
    }

    uploadSubmitButton.disabled = true;
    uploadSubmitButton.textContent = '등록 중…';
    setUploadStatus('파일을 검증하고 보고 데이터를 누적하고 있습니다.');
    try {
      const formData = new FormData();
      formData.append('file', file);
      const response = await fetch('/api/reports/upload', {
        method: 'POST',
        headers: { 'X-Report-Import-Password': uploadPasswordInput.value },
        body: formData
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message || '업로드를 완료하지 못했습니다.');

      setUploadStatus(result.importSummary || result.message, 'success');
      const dates = result.dates || [];
      if (dates.length) {
        populateReportDates(dates);
        document.getElementById('last-sync').textContent = formatDate(dates[dates.length - 1]);
        await loadReport(dates[dates.length - 1]);
      }
      uploadPasswordInput.value = '';
      uploadForm.reset();
      uploadFileName.textContent = 'XLSB 파일 선택';
      closeUploadDialog();
      showToast(result.message || '보고 데이터를 등록했습니다.');
    } catch (error) {
      setUploadStatus(error.message || '업로드 중 오류가 발생했습니다.', 'error');
    } finally {
      uploadSubmitButton.disabled = false;
      uploadSubmitButton.textContent = '등록하기';
    }
  });

  document.getElementById('download-report').addEventListener('click', exportReport);
  document.getElementById('guide-action').addEventListener('click', () => showToast('거래처·담당자·자유 메모는 대시보드에 저장하지 않습니다.'));

  async function initialize() {
    try {
      const response = await fetch('/api/reports/dates');
      if (!response.ok) throw new Error('보고일 목록을 불러오지 못했습니다.');
      const dates = await response.json();
      if (!dates.length) {
        setUnavailable('등록된 보고서가 없습니다. 운영자가 XLSB 보고서를 등록해야 합니다.');
        return;
      }
      populateReportDates(dates);
      document.getElementById('last-sync').textContent = formatDate(dates[dates.length - 1]);
      let savedDate = '';
      try {
        savedDate = window.localStorage.getItem('3pl-report-date') || '';
      } catch (_) {
        // Fall through to the most recently imported report.
      }
      await loadReport(dates.includes(savedDate) ? savedDate : dates[dates.length - 1]);
    } catch (error) {
      setUnavailable('보고 데이터를 불러오지 못했습니다.');
      showToast(error.message || '보고 데이터 조회 중 오류가 발생했습니다.');
    }
  }

  initialize();
})();
