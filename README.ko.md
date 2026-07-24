# Journeys Test

*[English](README.md) | 한국어*

Journey를 당신의 CLI 에이전트로 기기에서 실행하고, 결과를 표준 JUnit/Gradle 테스트 리포트로 받는 Gradle 플러그인.

## Journey란?

Journey는 앱의 사용자 흐름을 자연어로 적은 end-to-end 테스트다. 코드 대신 "검색 아이콘을 누른다", "결과 목록이 보이는지 확인한다" 같은 스텝을 XML로 나열하면, 에이전트가 기기에서 그대로 수행하고 각 스텝의 성공 여부를 판정한다. Android Studio가 Gemini로 제공하는 기능이며, 자세한 소개는 공식 문서 [Journeys for Android Studio](https://developer.android.com/studio/gemini/journeys)를 참고한다.

journey 파일은 Android Studio와 같은 위치, 즉 모듈의 `src/journeysTest/` 아래에 `*.journey.xml`로 둔다.

```
app/src/journeysTest/search.journey.xml
```

```xml
<journey name="search">
  <description>검색으로 항목을 찾는다</description>
  <actions>
    <action>Tap the search icon</action>
    <action>Type "compose" into the search field</action>
    <action>Verify a result list is shown</action>
  </actions>
</journey>
```

Android Studio는 이 journey를 Google 백엔드로 실행한다. 이 플러그인은 실행 주체만 당신이 고른 에이전트로 바꾼다. Google Cloud 인증이나 특정 백엔드 없이 원하는 모델을 쓰며, 폴더와 확장자, 태스크 이름(`journeysTest`)이 Studio와 같아 같은 파일을 양쪽에서 그대로 공유한다.

## 설정

플러그인을 적용하고, 사용할 에이전트 CLI만 지정하면 된다. journey를 수행하는 프롬프트는 플러그인에 내장돼 있어(Android Studio Journeys와 같은 방식으로 각 action을 순서대로 수행하고 check/verify는 현재 화면만 검사하며 스텝마다 PASSED/FAILED를 판정한다), 플러그인이 이 명령 뒤에 알아서 덧붙인다.

```kotlin
// build.gradle.kts
plugins { id("io.github.fornewid.journeys-test") version "0.1.0" }

journeys {
    agentCommand.set("claude -p --allowedTools Bash")
}
```

헤드리스 모드를 지원하는 에이전트면 무엇이든 쓸 수 있고, 이 값만 바꾸면 된다.

- Claude Code: `claude -p --allowedTools Bash`
- Codex: `codex exec --sandbox danger-full-access`
- Antigravity: `agy -p`

내장 프롬프트를 직접 바꾸고 싶으면 `prompt`로 덮어쓴다. 그 안의 `{journey}`가 journey 파일의 절대경로로 치환된다.

## 실행

```bash
./gradlew journeysTest
```

결과물은 두 가지다.

- `build/journey-results/*.xml`: 표준 JUnit XML 포맷. CI 테스트 리포터에 이 경로를 지정하면 수집된다.
- `build/journeys/report.html`: 스텝별 결과 뷰(아래 참고).

## 에이전트 계약

플러그인은 판정 로직을 갖지 않는다. 실제 판단은 `agentCommand`가 가리키는 에이전트가 한다(Claude Code, Gemini CLI 등 모델 무관). 에이전트는 기기를 구동한 뒤 결과를 아래 JSON으로 만들어 `<<<VERDICT>>>`와 `<<<END>>>` 마커 사이에 출력하면 된다.

```json
{"journey":"...","results":[{"action":"...","status":"PASSED","reasoning":"...","artifacts":["shots/01.png"]}]}
```

## 샘플

```bash
./gradlew :sample:journeysTest
```

기기 없이 바로 통과한다. `tools/echo-agent.sh`가 각 스텝을 PASSED로 보고하는 데모 에이전트다. 실제 UI 테스트를 하려면 `sample/build.gradle.kts`의 `agentCommand`를 진짜 에이전트로 바꾸고 에뮬레이터를 연결한다.

## 결과 뷰

`journeysTest`를 돌리면 `build/journeys/report.html`이 함께 만들어진다. 스텝마다 성공/실패와, 에이전트가 스크린샷을 남겼다면 그 화면을 브라우저에서 한눈에 볼 수 있다.

<img src="docs/result-view.png" alt="결과 뷰 예시" width="480">
