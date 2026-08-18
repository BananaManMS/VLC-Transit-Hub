import java.security.KeyStore
import java.security.MessageDigest

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.vlctransithub.wkmzqy"
    minSdk = 24
    targetSdk = 36
    versionCode = 16
    versionName = "1.6.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

abstract class ValidateKeystoreTask : DefaultTask() {
  @get:InputFile
  abstract val keystoreFile: RegularFileProperty

  @get:Input
  abstract val expectedFingerprint: Property<String>

  @TaskAction
  fun validate() {
    val file = keystoreFile.get().asFile
    if (!file.exists()) {
      throw GradleException("[KEYSTORE ERROR] debug.keystore does not exist at ${file.absolutePath}")
    }
    val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
    file.inputStream().use { isStream ->
      keystore.load(isStream, "android".toCharArray())
    }
    val cert = keystore.getCertificate("androiddebugkey")
      ?: throw GradleException("[KEYSTORE ERROR] Alias 'androiddebugkey' not found in debug.keystore")
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(cert.encoded)
    val sb = StringBuilder()
    for (i in digest.indices) {
      if (i > 0) sb.append(":")
      sb.append(String.format("%02X", digest[i]))
    }
    val actualFingerprint = sb.toString()
    val expected = expectedFingerprint.get()

    if (!actualFingerprint.equals(expected, ignoreCase = true)) {
      throw GradleException(
        """
        ================================================================================
        [CRITICAL KEYSTORE FINGERPRINT MISMATCH]
        The debug.keystore SHA-256 fingerprint does not match the expected certificate!
        Expected: $expected
        Actual:   $actualFingerprint
        
        Refusing to build: Compiling with a mismatched keystore will trigger
        INSTALL_FAILED_UPDATE_INCOMPATIBLE on existing user installations.
        ================================================================================
        """.trimIndent()
      )
    }
    println("[KEYSTORE VERIFIED] SHA-256 fingerprint: $actualFingerprint (MATCHES EXPECTED)")
  }
}

val expectedDebugFingerprint = "DB:D6:9B:5E:04:54:3F:F9:2F:A1:92:3A:65:EE:95:BA:EB:6D:1B:8A:11:39:52:2B:AB:D3:DE:9C:49:51:9F:80"

val validateKeystoreFingerprint = tasks.register<ValidateKeystoreTask>("validateKeystoreFingerprint") {
  keystoreFile.set(layout.projectDirectory.file("${rootDir}/debug.keystore"))
  expectedFingerprint.set(expectedDebugFingerprint)
}

tasks.named("preBuild") {
  dependsOn(validateKeystoreFingerprint)
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.work.runtime.ktx)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation("androidx.core:core-splashscreen:1.0.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation("com.google.code.gson:gson:2.11.0")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-gson:2.11.0")
  implementation(libs.play.services.location)
  implementation("org.osmdroid:osmdroid-android:6.1.20")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
