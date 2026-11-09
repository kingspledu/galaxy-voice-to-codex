# Galaxy S23 Ultra 실행 세션 기록

이 문서는 실제 Galaxy 연결 후 작성하는 기록지입니다. 현재 환경에는 연결된 기기가 없습니다.

## 연결 전

- 모델: Samsung Galaxy S23 Ultra
- Android 버전:
- 보안 패치:
- 사용 가능한 저장공간:
- 배터리:
- USB 디버깅 승인: [ ]
- 설치 APK 버전: `0.2.0-debug`
- 설치 명령: `powershell -ExecutionPolicy Bypass -File .\scripts\install-device.ps1`

## 모델별 오프라인 측정

각 모델마다 새 녹음 또는 동일하게 복사한 10초/30초/60초 한국어 파일을 사용합니다. 변환 전 기존 전사 칸은 비우고 저장합니다.

| 모델 | 파일 | 다운로드 시간 | 변환 시간 | 전사 문자 수 | PSS 전 | PSS 최고 | PSS 후 | 발열/배터리 | 결과 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| base | 10초 | | | | | | | | |
| base | 30초 | | | | | | | | |
| base | 60초 | | | | | | | | |
| small Q5_1 | 30초 | | | | | | | | |
| large-v3-turbo Q5_0 | 30초 | | | | | | | | |

### 측정 명령

앱을 실행한 뒤 변환 시작 직전에 다음을 별도 PowerShell에서 반복 실행합니다.

```powershell
adb shell dumpsys meminfo com.kuiro.youtubemarker | Select-String "TOTAL"
adb shell top -b -n 1 -p $(adb shell pidof com.kuiro.youtubemarker)
```

PSS 전/후 값은 작업 결과에 기록되지만 최고 메모리 사용량을 뜻하지 않습니다. 변환 중 명령을 반복해 최고값을 기록합니다.

## 오프라인 확인

1. 모델 다운로드와 설치 상태 확인을 끝냅니다.
2. `adb shell cmd connectivity airplane-mode enable`을 실행하거나 휴대전화에서 비행기 모드를 켭니다.
3. Wi-Fi와 모바일 데이터가 꺼진 것을 확인합니다.
4. 녹음 저장 → 기기 내 무료 음성 변환 → 결과 확인 → 검색 → ZIP 백업을 순서대로 실행합니다.
5. 모델 다운로드·변환이 네트워크 없이 동작하는지 기록합니다.
6. 마지막에 `adb shell cmd connectivity airplane-mode disable` 또는 휴대전화 설정으로 복원합니다.

## 실패 기록

- 모델 파일명/크기/SHA-256:
- 실패 단계:
- 화면 메시지:
- `adb logcat` 관련 줄:
- 원본 녹음 존재 여부:
- Room 상태:
- 다시 시도한 모델:

실패해도 녹음 파일이 보존되는지 반드시 확인합니다. 유료 API나 외부 서버를 대체 수단으로 사용하지 않습니다.
