// 최상위 빌드 파일: 모든 하위 프로젝트/모듈에 공통적인 구성 옵션을 추가할 수 있습니다.
plugins {
    // 안드로이드 애플리케이션 플러그인 적용 (실제 적용은 app 모듈에서)
    alias(libs.plugins.android.application) apply false
    // 코틀린 안드로이드 플러그인 적용 (실제 적용은 app 모듈에서)
    alias(libs.plugins.kotlin.android) apply false
}