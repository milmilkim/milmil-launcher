# milmil-launcher

크레마 팔레트(Crema Palette) 전용 초경량 개인 런처. 구현 계획은 대화로 확정된 플랜 문서를 따른다.

## 실기기 확인된 사실 (2026-07-07)

- 기기: Crema Palette (`model:CREMA_PALETTE`)
- Android 14 (SDK 34)
- 화면: 1264x1680, 320dpi (≈ 632x840dp)
- 물리키: 위 = 92 `KEYCODE_PAGE_UP`, 아래 = 93 `KEYCODE_PAGE_DOWN` (`onKeyDown`으로 정상 수신)
- APK 사이드로드 가능. 단, adb 설치 시 기기 베리파이어가 막으므로 최초 1회 다음 설정 필요:
  `adb shell settings put global verifier_verify_adb_installs 0`

## 설계 원칙 (요약)

- Java + platform View/XML만 사용. AndroidX/AppCompat/Kotlin/Compose 금지, 외부 의존성 0개 유지
- 애니메이션/위젯/백그라운드 서비스/네트워크/타이머 없음 (idle 작업 0)
- 텍스트와 선은 완전한 검정, 페이지 전환은 즉시 교체
- targetSdk 28은 의도적 (사이드로드 전용, lint의 ExpiredTargetSdkVersion 검사만 비활성화)
- 저장은 SharedPreferences만 사용

## 빌드

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
