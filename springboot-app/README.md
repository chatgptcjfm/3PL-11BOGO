# Integration Process Bootstrap (0차 환경 구성)

통합공정도 자동화의 실제 업무 기능 개발 전, Java 17 + Spring Boot 3.x + MariaDB + Apache POI 실행 환경을 구성하고 연결/Excel/API smoke test를 수행하기 위한 기반 프로젝트입니다. 실제 업무 규칙이나 공정 자동화 기능은 포함하지 않습니다.

## 기술 스택

- Java: Eclipse Temurin 17
- Spring Boot: 3.5.16
- 빌드: Maven 3.9.x
- 데이터베이스: MariaDB 11.x
- Excel/OOXML: Apache POI 5.5.1 (`poi-ooxml`, `poi-ooxml-full`)

## 엔드포인트

- `GET /` — 3PL 일일보고 통합 대시보드 화면
- `GET /api/health` — Spring Boot 응답과 MariaDB `SELECT VERSION()`을 함께 확인
- `GET /api/reports/dates` — 등록된 보고일 목록
- `GET /api/reports/{yyyy-MM-dd}` — 날짜별 KPI, 센터 및 익명 이슈 집계
- `POST /api/reports/upload` — 관리자 암호(`X-Report-Import-Password`) 검증 후 10MB 이하 `.xlsb` 일일보고를 DB에 누적
- `GET /api/excel/sample` — 샘플 `.xlsx` 파일 다운로드
- `POST /api/excel/analyze` — `multipart/form-data`의 `file` 필드로 `.xlsx` 또는 `.xlsm` 업로드 후 시트/행 미리보기 JSON 반환 (10 MB 제한)

## 3PL 일일보고 화면 초안

`GET /`에서 통합 대시보드를 제공합니다. 출고 KPI 아래의 탭에서 센터현황, 결품·미배송 현황, 자원현황을 각각 확인할 수 있습니다. 센터현황은 이전 화면 구성대로 센터별 출고량/주문량, 납품률, 미배송량을 한눈에 비교하고, 자원현황은 센터 탭을 선택해 `센터거점`(거점 수·면적), `인원`(사무·현장·합계), `장비`(지게차·HPT) 정보를 확인합니다. 자원현황 영역을 마우스 오른쪽 버튼으로 클릭하면 `자원현황 변경` 메뉴가 나타나며, 관리자 암호 확인 후 선택 보고일의 센터 수치를 저장할 수 있습니다. 보고서에 자원 데이터가 없는 날짜는 안내 문구와 빈 값으로 표시합니다. 이슈 집계의 `상세` 버튼은 원인, 보고일, 센터, 유형, 건수와 수량을 표시합니다. 경남센터(제지)는 원본 보고서 기준으로 KG, 그 외 센터는 BOX 단위로 표시합니다. 보고일 선택기 옆의 `엑셀 보고서 등록` 버튼으로 XLSB 파일을 등록할 수 있으며, 파일 크기는 10MB 이하입니다. 관리자 암호는 로컬 `.env`에 저장하고 화면에서는 입력값을 기억하지 않습니다.

업로드된 일일보고의 요약 KPI와 센터별 출고/미배송 집계를 MariaDB에 파일명에 포함된 날짜 기준으로 누적합니다(예: `26.10.12` → 2026-10-12). 엑셀 내부의 날짜 셀과 다른 경우에도 파일명 날짜를 보고일로 사용하며, 같은 날짜 파일을 다시 가져오면 해당 날짜 집계와 상세 내용을 모두 갱신합니다. 센터별 납품율은 원본 시트의 납품율 셀을 그대로 반영하므로 주문량이 0인 센터도 원본에 100%로 기록되어 있으면 100%로 표시합니다. 전일 발생한 미배송이 당일 요약의 미배송 잔량에 포함되고 수량이 일치하면 이슈 집계에 `미배송` 이월로 함께 포함해 센터별 잔량과 이슈 상세 합계를 맞춥니다. 보고일 입력칸은 등록된 보고일만 활성화하는 달력형 선택기를 제공합니다. 보고서가 없는 날짜는 회색으로 비활성 표시되며, 옆의 `최근 날짜` 버튼으로 가장 최근 등록일을 바로 열 수 있습니다. 원본 엑셀, 거래처명, 담당자명, 상세 메모는 DB나 대시보드에 저장하지 않고 보고서에서 확인되는 원인명만 익명 집계합니다. Google Sheets/Looker Studio 실시간 연동과 처리상태·완료일 입력은 아직 구현하지 않았습니다.

## 샌드박스 재구축

샌드박스가 초기화된 뒤 `/home/user/webapp/springboot-app`에서 실행합니다. Debian 기반 Linux, sudo 권한, 네트워크 접근이 필요합니다.

```bash
./scripts/setup-sandbox.sh
./scripts/create-dev-env.sh
./scripts/configure-dev-db.sh
./scripts/start-local.sh
```

`setup-sandbox.sh`는 Maven, MariaDB server/client 및 Java 17을 준비하고 MariaDB 서비스를 시작합니다. `create-dev-env.sh`는 무작위 DB 비밀번호와 보고서 업로드 암호를 `.env`에 저장하며 파일 권한은 `600`입니다. `.env`는 Git에서 제외됩니다. `configure-dev-db.sh`는 해당 설정으로 개발 DB와 전용 계정을 생성합니다. 앱 시작 시 `schema.sql`이 누적 보고용 테이블을 준비합니다.

실행 후 주소: `http://127.0.0.1:3000`

### XLSB 일일보고 누적 등록

최초 한 번 `python3 -m venv .venv-import && .venv-import/bin/pip install -r requirements-import.txt`로 XLSB 판독기를 준비한 뒤, `springboot-app/`에서 다음처럼 보고서를 등록합니다.

```bash
.venv-import/bin/python scripts/import-reports.py \
  "/home/user/uploaded_files/(LGL) 깨끗한나라 3PL센터 일일보고_26.10.15.xlsb" \
  "/home/user/uploaded_files/(LGL) 깨끗한나라 3PL센터 일일보고_26.10.17.xlsb"
```

대시보드의 `엑셀 보고서 등록` 버튼은 같은 `scripts/import-reports.py`를 호출하며, 관리자 업로드 암호는 서버의 `.env`에 둡니다. 등록은 보고일 기준으로 멱등 처리됩니다. 동일 날짜 파일은 재등록 시 해당 날짜를 갱신하고, 새 보고일은 누적됩니다. 원본 파일은 임시 경로에서 처리 후 삭제하며 저장소에 복사하지 않습니다.

```bash
curl -fsS http://127.0.0.1:3000/api/health
curl -f http://127.0.0.1:3000/api/excel/sample -o /tmp/process-sample.xlsx
curl -f -F 'file=@/tmp/process-sample.xlsx' http://127.0.0.1:3000/api/excel/analyze
```

## 테스트 및 프로세스 관리

```bash
mvn -B test
mvn -B clean package
pm2 status
pm2 logs integration-process-bootstrap --nostream
pm2 delete integration-process-bootstrap
```

`./scripts/start-local.sh`는 MariaDB 시작, DB 사용자/DB 보정, Maven test/package, PM2 실행, `/api/health` smoke test를 순서대로 수행합니다. 앱은 PM2가 관리하며 `PORT` 환경 변수로 포트를 바꿀 수 있습니다.

## 배포 메모

Cloudflare Pages/Workers는 Java/Spring Boot JVM 프로세스를 직접 실행하는 배포 대상이 아닙니다. Genspark Code의 현재 샌드박스에서 PM2와 `GetServiceUrl`로 확인 가능한 URL은 미리보기/검증용이며, 영속 운영 서버로 취급하면 안 됩니다.

MariaDB를 그대로 유지하는 1순위 권장안은 **Linux VPS**입니다. Java 17 런타임과 MariaDB를 같은 VPS 또는 사설 네트워크에 두고, Spring Boot JAR를 systemd로 실행하거나 이 디렉터리의 Dockerfile로 앱만 컨테이너화할 수 있습니다. DB 포트는 공개하지 않고, 방화벽·백업·TLS·비밀 설정은 운영 환경에서 별도 구성해야 합니다.

PaaS 대안으로 Railway는 Spring Boot 앱을 GitHub 또는 Dockerfile로 배포하는 공식 가이드를 제공합니다. 다만 Railway의 관리형 DB 문서는 MySQL 서비스를 안내하며 MariaDB 관리형 인스턴스는 확인되지 않았습니다. 따라서 MariaDB 고정 조건에서는 별도의 MariaDB VPS/서비스에 연결하거나 자체 MariaDB 컨테이너의 저장소·백업·업데이트를 직접 관리해야 합니다. Render 역시 Java 앱을 Docker로 구동할 수 있지만, 관리형 DB는 이 조건에서 MariaDB 대체재로 간주하지 않습니다.

- Railway Spring Boot: https://docs.railway.com/guides/spring-boot
- Railway MySQL: https://docs.railway.com/databases/mysql
- Render 언어 지원/Docker: https://render.com/docs/language-support
- Spring Boot 시스템 요구사항: https://docs.spring.io/spring-boot/system-requirements.html

아직 외부 서버 배포는 하지 않았습니다. 실제 배포는 운영 제공자/계정과 비용·지역 선택 후 별도 승인과 자격 증명으로 진행해야 합니다.
