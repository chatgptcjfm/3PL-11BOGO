# Integration Process Bootstrap (0차 환경 구성)

통합공정도 자동화의 실제 업무 기능 개발 전, Java 17 + Spring Boot 3.x + MariaDB + Apache POI 실행 환경을 구성하고 연결/Excel/API smoke test를 수행하기 위한 기반 프로젝트입니다. 실제 업무 규칙이나 공정 자동화 기능은 포함하지 않습니다.

## 기술 스택

- Java: Eclipse Temurin 17
- Spring Boot: 3.5.16
- 빌드: Maven 3.9.x
- 데이터베이스: MariaDB 11.x
- Excel/OOXML: Apache POI 5.5.1 (`poi-ooxml`, `poi-ooxml-full`)

## 엔드포인트

- `GET /api/health` — Spring Boot 응답과 MariaDB `SELECT VERSION()`을 함께 확인
- `GET /api/excel/sample` — 샘플 `.xlsx` 파일 다운로드
- `POST /api/excel/analyze` — `multipart/form-data`의 `file` 필드로 `.xlsx` 또는 `.xlsm` 업로드 후 시트/행 미리보기 JSON 반환 (10 MB 제한)

## 샌드박스 재구축

샌드박스가 초기화된 뒤 `/home/user/webapp/springboot-app`에서 실행합니다. Debian 기반 Linux, sudo 권한, 네트워크 접근이 필요합니다.

```bash
./scripts/setup-sandbox.sh
./scripts/create-dev-env.sh
./scripts/configure-dev-db.sh
./scripts/start-local.sh
```

`setup-sandbox.sh`는 Maven, MariaDB server/client 및 Java 17을 준비하고 MariaDB 서비스를 시작합니다. `create-dev-env.sh`는 무작위 로컬 DB 비밀번호를 `.env`에 저장하며 파일 권한은 `600`입니다. `.env`는 Git에서 제외됩니다. `configure-dev-db.sh`는 해당 설정으로 개발 DB와 최소 권한의 전용 계정을 생성합니다.

실행 후 주소: `http://127.0.0.1:3000`

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
