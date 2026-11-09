# 빌드 검증 기록

## 무료 Whisper 전환 검증 — 2026-11-10

- 유료 음성 API, Groq/OpenAI 키, 중계 서버, HTTP 음성 업로드 코드 제거
- Android `whisper.cpp` v1.7.6 JNI 엔진 연결, ARM64 전용 빌드 설정
- 로컬 M4A → PCM mono/16kHz 디코딩, 최대 2분 입력 제한, 취소·실패 시 원본 보존
- 무료 모델 선택: base, small Q5_1, large-v3-turbo Q5_0
- 모델 revision·파일 크기·SHA-256 검증 및 임시 파일 설치 구현
- WorkManager 기반 모델 다운로드와 로컬 변환 재시도 큐 구현
- 설정 화면에서 모델 다운로드·자동 로컬 변환만 제공. 결제·유료 전환 메뉴 없음
- whisper.cpp 소스 SHA-256 고정 및 MIT 라이선스 APK assets 포함
- 영문 임시 드라이브 별칭으로 네이티브 `assembleDebug lintDebug testDebugUnitTest` 진행 확인: CMake, ARM64 native build, APK packaging 단계 통과
- 현재 최종 APK: `app/build/outputs/apk/debug/app-debug.apk` (10,562,637 bytes)
- 한글 원래 경로의 CMake 실행은 Windows 파일시스템 오류로 실패할 수 있어 `scripts/invoke-gradle.ps1`가 임시 영문 드라이브 별칭을 사용하도록 보완
- Galaxy S23 Ultra 설치·모델 다운로드·오프라인 전사·속도·PSS·발열은 아직 미검증
- 현재 `adb devices` 결과에 연결된 기기 없음
- 모델 파일은 APK에 포함하지 않음. 최초 모델 다운로드 후 비행기 모드에서 별도 검증 필요
- 무료 엔진 상세와 검증 절차: `docs/FREE-WHISPER.md`

이하 내용은 이전 단계 당시 기록입니다.

## 메모 정리·백업 소스 검증 — 2026-11-09

- 상세 편집·삭제·처리 대기 필터·검색 확장, 수동 자막 및 요약 저장 구현
- 순수 포맷팅 모듈로 Gemini 프롬프트 생성·복사, 사용자 메모/영상 자막 출처 구분
- 시스템 문서 선택기를 통한 JSON+녹음 ZIP 내보내기 구현. 원본 파일은 변경하지 않음
- 저장 시 수정 시각 대조, 녹음 중 변경 차단, 저장하지 않은 편집 닫기 확인
- 기존 테이블 열을 사용하여 DB 버전/스키마 변경 없음
- `assembleDebug lintDebug testDebugUnitTest`: 성공
- 단위 테스트 6개 통과, 실패/건너뛰기 0개
- Lint 오류 0개, 경고 15개. APK 서명 검증 성공(exit 0)
- APK: 10,292,363 bytes
- Galaxy 연결 없음. 상세 화면·클립보드·문서 제공자·실제 백업 재생은 미검증
- Whisper·자동 자막·외부 AI API·GitHub 배포는 실행하지 않음
- 사용·실기기 검증: `MEMO-ORGANIZE-TEST.md`

이하 내용은 이전 단계 당시 기록입니다.

## 플로팅 수집 소스 검증 — 2026-11-09

- 이동·위치 저장 가능한 오버레이 버튼과 별도 작업의 작은 메모 창 구현
- 마이크 포그라운드 서비스 및 알림 종료·저장 구현
- 메인 화면/팝업의 녹음 소유권 충돌 방지 및 프로세스당 1회 중단 복구 적용
- 최종 `assembleDebug lintDebug testDebugUnitTest`: 성공
- Lint: 오류 0개, 경고 13개. 의존성, kapt, 백업 규칙, 아이콘, 문자열, 미사용 리소스, 터치 접근성 관련 경고가 남음
- 단위 테스트 1개 통과: 다른 창은 진행 중인 녹음의 소유권을 빼앗거나 해제할 수 없음
- Windows JDK 17의 한글 경로 인수 파일 문제는 테스트 실행에서 `-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=MS949`로 보완. `scripts/test-local.ps1`에 해당 컴퓨터 조건 반영
- APK: 10,292,305 bytes. 서명 검증 성공 및 DEX 내 오버레이·녹음 서비스·Room 생성 클래스 포함 확인
- ADB 기기 없음. 오버레이/권한/백그라운드 녹음/YouTube 복귀는 실기기 미검증
- 사용·검증 절차: `FLOATING-DEVICE-TEST.md`

이하 내용은 이전 단계 당시 기록입니다.

## 2단계 소스 검증 — 2026-11-09

- 앱 내 마이크 권한 요청, AAC/M4A 녹음, 종료·화면 이탈 시 저장 구현
- Room Entity/DAO/DB 및 비동기 저장, 목록 검색·재생·수동 시점 링크 구현
- `assembleDebug lintDebug`: 성공
- Lint: 오류 0개, 경고 12개 (의존성 갱신, kapt, 백업 규칙, 아이콘, 문자열 및 미사용 리소스)
- APK: 10,292,247 bytes, `apksigner verify` 성공
- ADB 연결 기기 없음. 실기기 테스트는 미실시
- 정상 종료 파일의 실제 재생, 비정상 종료·권한 거부·회전 동작은 `STAGE2-DEVICE-TEST.md` 절차로 확인 필요

아래는 1단계 당시 기록입니다.

2026-11-09, Windows 환경에서 확인했습니다.

- JDK: Temurin 17.0.21
- Gradle Wrapper: 8.9
- Android SDK: API 35
- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- `assembleDebug`: 성공
- APK: `app/build/outputs/apk/debug/app-debug.apk` (5,871,874 bytes)
- `apksigner verify --verbose`: 성공, APK Signature Scheme v2
- ADB 기기 목록: 연결된 기기 없음

Windows 한글 경로 검사를 `android.overridePathCheck=true`로 해제했고 Java/Kotlin JVM 타깃을 17로 통일했습니다.

이 결과는 컴파일과 APK 서명 검증입니다. Galaxy S23 Ultra 설치·화면·공유 인텐트 실행 검증은 아직 하지 않았습니다. 녹음, 플로팅 UI, Room, Whisper, 자막 수집도 아직 구현되지 않았습니다. GitHub 배포는 진행하지 않았습니다.

## 실기기 설치

1. Galaxy 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호를 7회 눌러 개발자 옵션을 켭니다.
2. 개발자 옵션에서 USB 디버깅을 켜고 데이터 통신이 가능한 USB 케이블로 연결합니다.
3. 휴대전화에 표시되는 PC 디버깅 허용 창에서 승인합니다.
4. 프로젝트 폴더의 PowerShell에서 아래 명령을 실행합니다.

```powershell
.\.tools\android-sdk\platform-tools\adb.exe devices
.\.tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

기기 목록이 `device` 상태여야 설치할 수 있습니다. `unauthorized`라면 휴대전화 승인 창을 확인합니다.
