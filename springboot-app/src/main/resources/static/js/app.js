(() => {
  const rows = document.getElementById('issue-rows');
  const searchInput = document.getElementById('issue-search');
  const filterButtons = [...document.querySelectorAll('.filter-tab')];
  const issueTotal = document.getElementById('issue-total');
  const navIssueCount = document.getElementById('nav-issue-count');
  const visibleCount = document.getElementById('visible-count');
  const dialog = document.getElementById('issue-dialog');
  const form = document.getElementById('issue-form');
  const toast = document.getElementById('toast');
  let activeFilter = 'all';
  let addedIssues = 0;
  let toastTimer;

  const initialCounts = { all: 37, 결품: 23, 미배송: 14 };

  const nowLabel = () => new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric'
  }).format(new Date());

  document.getElementById('today-label').textContent = nowLabel();

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.remove('is-visible'), 3000);
  }

  function applyFilters() {
    const term = searchInput.value.trim().toLocaleLowerCase('ko-KR');
    let visible = 0;
    [...rows.querySelectorAll('tr')].forEach((row) => {
      const typeMatches = activeFilter === 'all' || row.dataset.type === activeFilter;
      const searchMatches = !term || `${row.dataset.search || ''} ${row.textContent}`
        .toLocaleLowerCase('ko-KR').includes(term);
      const show = typeMatches && searchMatches;
      row.hidden = !show;
      if (show) visible += 1;
    });
    visibleCount.innerHTML = `${visible}개 이슈 표시 중 <b>·</b> 샘플 데이터`;
  }

  function updateCounts() {
    const total = initialCounts.all + addedIssues;
    issueTotal.textContent = total;
    navIssueCount.textContent = total;
    document.querySelector('[data-filter="all"] span').textContent = total;
    document.querySelector('[data-filter="결품"] span').textContent = initialCounts.결품 + countAdded('결품');
    document.querySelector('[data-filter="미배송"] span').textContent = initialCounts.미배송 + countAdded('미배송');
  }

  function countAdded(type) {
    return [...rows.querySelectorAll('tr[data-new="true"]')]
      .filter((row) => row.dataset.type === type).length;
  }

  function makeTextCell(text) {
    const cell = document.createElement('td');
    cell.textContent = text;
    return cell;
  }

  function addIssueRow(issue) {
    const row = document.createElement('tr');
    row.dataset.type = issue.type;
    row.dataset.new = 'true';
    row.dataset.search = `${issue.center} ${issue.item} ${issue.reason} ${issue.owner}`;

    const centerCell = document.createElement('td');
    const productCell = document.createElement('div');
    productCell.className = 'product-cell';
    const avatar = document.createElement('span');
    avatar.className = 'center-avatar avatar-blue';
    avatar.textContent = issue.center.slice(0, 1);
    const details = document.createElement('span');
    const center = document.createElement('strong');
    center.textContent = issue.center;
    const item = document.createElement('small');
    item.textContent = issue.item;
    details.append(center, item);
    productCell.append(avatar, details);
    centerCell.append(productCell);

    const typeCell = document.createElement('td');
    const type = document.createElement('span');
    type.className = `type-pill ${issue.type === '결품' ? 'type-stockout' : 'type-delivery'}`;
    const typeDot = document.createElement('i');
    type.append(typeDot, document.createTextNode(issue.type));
    typeCell.append(type);

    const quantityCell = document.createElement('td');
    const quantity = document.createElement('strong');
    quantity.className = 'quantity';
    quantity.textContent = issue.quantity;
    const unit = document.createElement('small');
    unit.className = 'unit-text';
    unit.textContent = issue.type === '결품' ? 'EA' : '건';
    quantityCell.append(quantity, unit);

    const reasonCell = document.createElement('td');
    const reason = document.createElement('span');
    reason.className = 'reason-code';
    reason.textContent = issue.reason;
    reasonCell.append(reason);

    const statusCell = document.createElement('td');
    const status = document.createElement('span');
    status.className = 'status-pill status-open';
    const statusDot = document.createElement('i');
    status.append(statusDot, document.createTextNode('신규 접수'));
    statusCell.append(status);

    const updatedCell = document.createElement('td');
    const time = document.createElement('span');
    time.className = 'updated-time';
    time.textContent = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());
    const owner = document.createElement('small');
    owner.className = 'owner-text';
    owner.textContent = issue.owner;
    updatedCell.append(time, owner);

    const actionCell = document.createElement('td');
    const action = document.createElement('button');
    action.type = 'button';
    action.className = 'row-more';
    action.setAttribute('aria-label', `${issue.center} 이슈 상세`);
    action.textContent = '···';
    action.addEventListener('click', () => showToast('이 상세 화면은 1차 개발 범위에서 연결됩니다.'));
    actionCell.append(action);

    row.append(centerCell, typeCell, quantityCell, reasonCell, statusCell, updatedCell, actionCell);
    rows.prepend(row);
    addedIssues += 1;
    updateCounts();
    applyFilters();
  }

  filterButtons.forEach((button) => {
    button.addEventListener('click', () => {
      activeFilter = button.dataset.filter;
      filterButtons.forEach((item) => item.classList.toggle('is-selected', item === button));
      applyFilters();
    });
  });

  searchInput.addEventListener('input', applyFilters);
  window.addEventListener('keydown', (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      searchInput.focus();
    }
  });

  document.getElementById('open-issue-modal').addEventListener('click', () => dialog.showModal());
  document.getElementById('close-issue-modal').addEventListener('click', () => dialog.close());
  document.getElementById('cancel-issue').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const formData = new FormData(form);
    addIssueRow({
      center: String(formData.get('center') || ''),
      type: String(formData.get('type') || '결품'),
      item: String(formData.get('item') || ''),
      quantity: String(formData.get('quantity') || '1'),
      reason: String(formData.get('reason') || '기타'),
      owner: String(formData.get('owner') || '미지정')
    });
    dialog.close();
    form.reset();
    showToast('샘플 이슈를 목록에 추가했습니다. 실제 저장은 1차 개발에서 연결됩니다.');
  });

  document.getElementById('download-report').addEventListener('click', () => {
    window.location.assign('/api/excel/sample');
    showToast('샘플 Excel 보고서를 다운로드합니다.');
  });
  document.getElementById('show-all-issues').addEventListener('click', () => {
    activeFilter = 'all';
    filterButtons.forEach((item) => item.classList.toggle('is-selected', item.dataset.filter === 'all'));
    searchInput.value = '';
    applyFilters();
    showToast('현재 목록은 화면 검증용 샘플 데이터입니다.');
  });
  document.getElementById('center-detail').addEventListener('click', () => showToast('센터별 상세 보고는 1차 개발에서 실제 데이터와 연결됩니다.'));
  document.getElementById('guide-action').addEventListener('click', () => showToast('원인 코드와 종결 기준은 화면 검증용 예시입니다.'));

  applyFilters();
})();
