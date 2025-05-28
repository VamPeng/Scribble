plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.parcelize)
//    alias(libs.plugins.ksp)
}

android {
  namespace = "com.demo.scribbledemo"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.demo.scribbledemo"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {

  implementation(project(":lib"))
  implementation(libs.rxjava3.rxjava)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.glide.glide)
  implementation(libs.stream)
//    ksp(libs.glide.ksp)
  implementation("com.tencent:mmkv:2.2.2")

  platform(libs.androidx.compose.bom).let { composeBom ->
    api(composeBom)
//    androidTestApi(composeBom)
  }

  api(libs.androidx.compose.material3)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.material)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.reactivestreams.ktx)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}