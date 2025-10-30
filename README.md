# LyriMood – 감정 기반 음악 분석 플랫폼

곡 제목과 가사를 입력하면 감정 좌표, 분위기 레이블, 태그, 장르, 어쿠스틱 정보를 추출해 주는 Spring Boot 애플리케이션입니다.  
Google Gemini, DetectLanguage, MusicBrainz, AcousticBrainz 등을 조합하여 분석 결과를 REST API와 웹 UI(`/logs`)로 제공합니다.

---

## 🚀 개발 환경 설정

### 사전 요구사항
- Java 21 이상
- Gradle Wrapper (저장소 포함)
- (선택) Docker – 외부 의존성 캐시용

### 1. 저장소 클론
```bash
git clone git@github.com:kimjieun666/LyriMood.git
cd LyriMood
```

### 2. 환경 변수 준비
```bash
cd backend
cp .env.example .env.local
echo 'GEMINI_API_KEY=your_api_key' >> .env.local
export $(grep -v '^#' .env.local | xargs)   # macOS/Linux
```
- `GEMINI_API_KEY`는 필수입니다. DetectLanguage 키가 없으면 내부 스텁이 동작합니다.

### 3. 백엔드 실행
```bash
GRADLE_USER_HOME=$(pwd)/.gradle ./gradlew bootRun
```
- 서버: http://localhost:8081  
- 로그 페이지: http://localhost:8081/logs  
- REST API: `POST /api/mood/analyze`

### 4. Docker로 실행 (선택)
```bash
docker compose up backend
```

---

## 🔧 주요 명령어
| 구분 | 명령어 | 설명 |
| --- | --- | --- |
| 백엔드 빌드 | `./gradlew build` | 전체 빌드 |
| 백엔드 실행 | `./gradlew bootRun` | Spring Boot 애플리케이션 실행 |
| 백엔드 테스트 | `./gradlew test` | 단위/통합 테스트 실행 |
| 로그 확인 | `./gradlew bootRun --info` | 실행 시 상세 로그 출력 |

---

## 🚀 주요 기능
- 🧠 **감정 분석**: Google Gemini로 valence·arousal, 감정 레이블, 요약, 하이라이트 문구, 욕설 여부 추출
- 🌐 **언어 판별**: DetectLanguage API(또는 스텁) + 한글 자모 대응 폴백으로 안정적인 언어 감지
- 🎧 **메타데이터 보강**: MusicBrainz/AcousticBrainz로 발매 정보, 키, 템포, 분위기 조회
- 📓 **로그 저장/조회**: H2 인메모리 DB에 로그를 저장하고 `/logs` 페이지에서 모달로 근거와 가사 확인
- 🏷️ **태그 추천**: 감정, 장르, 국가 정보를 기반으로 맞춤 태그 생성

---

## 🛠 기술 스택
### Backend
- Java 21, Spring Boot 3, Spring WebFlux, Spring Data JPA
- H2 Database (개발), RestClient(WebClient), Resilience 패턴(커스텀 실행기)
- Thymeleaf + Alpine.js UI, Gradle 빌드

### External APIs
- Google Gemini Generative Language API
- DetectLanguage API (미설정 시 로컬 스텁)
- MusicBrainz & AcousticBrainz

---

## 📦 프로젝트 구조
```
LyriMood/
├── backend/
│   ├── src/main/java/com/jieun/lyrimood/
│   │   ├── api/               # REST 엔드포인트
│   │   ├── application/       # 도메인 서비스
│   │   ├── domain/            # 엔티티 및 값 객체
│   │   ├── infrastructure/    # 외부 API 클라이언트, 저장소
│   │   └── shared/            # 공통 유틸리티
│   ├── src/main/resources/    # 설정, Thymeleaf 템플릿
│   └── src/test/              # 테스트 코드
└── README.md
```

---

## 🧪 테스트 실행
```bash
cd backend
GRADLE_USER_HOME=$(pwd)/.gradle ./gradlew test
```
- 네트워크 제한 환경에서는 Gradle 의존성 다운로드가 실패할 수 있으니, 필요 시 네트워크 허용 상태에서 실행하세요.

---

## 🔐 환경 변수
| 변수 | 설명 |
| --- | --- |
| `GEMINI_API_KEY` | Google Gemini API 키 |
| `GEMINI_MODEL` | (옵션) 사용할 Gemini 모델명, 기본 `gemini-2.5-flash` |
| `DETECTLANGUAGE_API_KEY` | DetectLanguage API 키 (없으면 스텁 사용) |
| `MUSICBRAINZ_BASE_URL`, `ACOUSTICBRAINZ_BASE_URL` | 외부 API 엔드포인트 재정의 |
| `EXTERNAL_*` | 외부 호출 타임아웃/재시도/레이트리밋 설정 |

세부 값은 `backend/src/main/resources/application.yml`을 참고하세요.

---

## 🤝 기여하기
1. Fork 저장소
2. 브랜치 생성: `git checkout -b feature/my-feature`
3. 변경 사항 커밋: `git commit -m 'Add my feature'`
4. 브랜치 푸시: `git push origin feature/my-feature`
5. Pull Request 생성

---

## 📎 참고 링크
- [Spring Boot Reference](https://docs.spring.io/spring-boot/)
- [Google Gemini API Docs](https://ai.google.dev/)
- [MusicBrainz API](https://musicbrainz.org/doc/Development/XML_Web_Service/Version_2)
- [AcousticBrainz API](https://acousticbrainz.org/api)

LyriMood는 음악 감정 분석과 데이터 보강 로직을 하나의 Spring 프로젝트로 통합해 빠르게 실험하고 결과를 공유할 수 있도록 설계되었습니다. 즐거운 해킹 되세요! 🎶
