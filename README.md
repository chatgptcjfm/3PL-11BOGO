# webapp workspace

워크스페이스에는 초기 Hono/Cloudflare 템플릿이 보존되어 있으며, 별도 Java 프로젝트는 `springboot-app/`에 구성되어 있습니다.

## Java / Spring Boot 0차 환경 검증

- 프로젝트와 실행 안내: [`springboot-app/README.md`](springboot-app/README.md)
- 기술 스택: Java 17, Spring Boot 3.5, Maven, MariaDB 11, Apache POI OOXML
- 개발 API: `GET /api/health`, `GET /api/excel/sample`, `POST /api/excel/analyze`
- 재구축 및 검증: `cd springboot-app && ./scripts/rebuild-and-verify.sh`

Cloudflare Pages/Workers는 Java/Spring Boot 프로세스를 직접 실행할 수 없습니다. Java 서버 배포 검토는 하위 README를 참고하십시오. 기존 Cloudflare 템플릿은 이번 작업에서 변경하지 않았습니다.
