# LyriMood Backend

Spring Boot 3 · Java 21 기반의 감정 분석 백엔드로, 하나의 애플리케이션 안에서 다음 API를 호출해 음악 무드를 산출합니다.

1. **MusicBrainz** – 발매 정보, 태그, annotation(가사 메모) 조회  
2. **DetectLanguage** – 가사의 언어와 신뢰도 측정 (환경 변수 없으면 기본 스텁 사용)  
3. **Gemini Generative Language** – valence/arousal, 무드 레이블, 요약, 욕설 여부, 근거 문장 생성  
4. **AcousticBrainz** – 키/템포/분위기 정보 보강 (MusicBrainz recording ID가 있을 때)  
5. 태그 후보를 로컬 키프레이즈 분석으로 보강한 뒤, 결과를 `analysis_logs` 테이블과 `/api/mood/analyze` 응답에 담습니다.

Thymeleaf 템플릿 `/logs` 페이지에서는 각 로그의 상세 근거와 가사를 모달로 확인할 수 있습니다.  
과거에 사용하던 Python/Flask `analyzer` 서비스는 제거되었으며, 별도 파이썬 실행은 필요하지 않습니다.

---

## 빠르게 실행하기

1. **환경 변수**
   ```bash
   cp .env.example .env.local               # 예시 파일이 있다면 사용
   echo 'GEMINI_API_KEY=your_api_key' >> .env.local
   export $(grep -v '^#' .env.local | xargs)   # macOS/Linux
   ```
   - `GEMINI_API_KEY`는 필수입니다.
   - `DETECTLANGUAGE_API_KEY`가 없으면 기본 스텁이 자동으로 선택됩니다.

2. **서버 실행**
   ```bash
   GRADLE_USER_HOME=$(pwd)/.gradle ./gradlew bootRun
   ```
   또는 Docker Compose를 사용할 경우:
   ```bash
   ./scripts/dev-up.sh
   ```

3. **REST API 호출 예시**
   ```bash
   curl -X POST http://localhost:8081/api/mood/analyze \
        -H 'Content-Type: application/json' \
        -d '{
              "title": "Easy On Me",
              "artist": "Adele",
              "lyrics": "There ain'\''t no gold in this river..."
            }'
   ```

4. **웹 UI**
   - `http://localhost:8081`에서 제목/가사를 입력해 분석 실행
   - `http://localhost:8081/logs`에서 최근 20건의 로그를 검색·열람

---

## API 개요

- **Endpoint**: `POST /api/mood/analyze`  
- **필드 요약**
  ```json
  {
    "label": "Bright · Calm",
    "valence": 0.813,
    "arousal": 0.655,
    "lang": "en",
    "profane": false,
    "tags": ["bright", "calm", "clean"],
    "genres": ["pop"],
    "highlights": ["감정 해석: ...", "AI 요약: ..."],
    "lyrics": "...",
    "releaseDate": "2021-10-15",
    "key": "C",
    "tempo": 84.2
  }
  ```
- 오류 시 `{ "message": "..." }`로 반환하며 외부 서비스 실패는 5xx입니다.
- `GET /logs`는 최신 로그를 보여주고 `query` 파라미터로 제목·아티스트·태그·장르를 검색할 수 있습니다.

---

## 구성 요소

- `DefaultMoodAnalysisService`: 위 4개의 외부 서비스와 로컬 태그 분석을 조합해 최종 응답과 로그를 생성
- `GeminiClient`, `DetectLanguageClient`, `MusicBrainzClient`, `AcousticBrainzClient`: WebClient 기반 외부 호출
- `ResilientExecutor`: 재시도·레이트리밋·타임아웃 공통 적용
- `LogController`: `/logs` 템플릿 렌더링
- `GlobalExceptionHandler`: 예외를 `{ "message": "..." }` 구조로 변환

---

## 환경 변수 요약

| 키 | 설명                                  |
| --- |-------------------------------------|
| `GEMINI_API_KEY` | Gemini API 키 (필수)                   |
| `GEMINI_MODEL` | (선택) 사용할 모델명, 기본 `gemini-2.5-flash` |
| `DETECTLANGUAGE_API_KEY` | DetectLanguage API 키                |
| `MUSICBRAINZ_BASE_URL` / `ACOUSTICBRAINZ_BASE_URL` | 외부 엔드포인트 재정의                        |
| `EXTERNAL_*` | 타임아웃·재시도·레이트리밋 세팅                   |
| `APP_PORT` | Docker Compose 실행 시 호스트 포트 지정       |

자세한 값은 `src/main/resources/application.yml`과 `.env` 파일을 참고하세요.

---

## 테스트

```bash
GRADLE_USER_HOME=$(pwd)/.gradle ./gradlew test
```

네트워크 제한이 있을 경우 의존성 다운로드 실패가 발생할 수 있습니다.

---

### 주의
- 현재 레포지토리에는 Python 파일이나 `analyzer` 디렉터리가 존재하지 않습니다. 모든 분석은 Java 코드로 수행됩니다.

