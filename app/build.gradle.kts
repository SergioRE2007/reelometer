import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.reelometro.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.reelometro.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 18
        versionName = "1.10.1"
    }

    androidResources {
        // Idiomas de la app: español (values/, el de por defecto) y cada carpeta values-xx que exista
        // (values-en, values-pt-rBR, values-b+sr+Latn). De las librerías solo se empaquetan esos.
        val appLocales = file("src/main/res").listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { Regex("values-([a-z]{2,3}(-r[A-Z]{2})?|b\\+[A-Za-z0-9+]+)").matchEntire(it.name)?.groupValues?.get(1) }
            .sorted()
        localeFilters += listOf("es") + appLocales
        // res/xml/locales_config.xml (idioma por app de Android 13+) lo genera AGP con esas mismas carpetas;
        // el idioma de values/ está en src/main/res/resources.properties.
        generateLocaleConfig = true
    }

    // Con un AAB, Play solo instalaría los idiomas del sistema; el idioma por app (y ResourceTexts, que
    // lee los recursos en el idioma de los textos) necesita todos en el dispositivo.
    bundle {
        language {
            enableSplit = false
        }
    }

    // Firma debug opcional con una llave fija (nunca en git), para que cada APK nueva se instale encima de la anterior.
    // Se lee de local.properties (reelometro.debugKeystore / …Password / …Alias) o de las variables de entorno
    // REELOMETRO_DEBUG_KEYSTORE / _PASSWORD / _ALIAS. Sin ella, la clave debug normal del equipo.
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
        localProps.getProperty(prop)?.trim()?.ifEmpty { null } ?: System.getenv(env)?.trim()?.ifEmpty { null }
    val fixedDebugKey = signingValue("reelometro.debugKeystore", "REELOMETRO_DEBUG_KEYSTORE")?.let { rootProject.file(it) }
    val fixedDebugPassword = signingValue("reelometro.debugKeystorePassword", "REELOMETRO_DEBUG_KEYSTORE_PASSWORD")
    val fixedDebugAlias = signingValue("reelometro.debugKeyAlias", "REELOMETRO_DEBUG_KEY_ALIAS") ?: "reelometro-debug"
    if (fixedDebugKey != null && fixedDebugKey.exists() && fixedDebugPassword != null) {
        signingConfigs {
            getByName("debug") {
                storeFile = fixedDebugKey
                storePassword = fixedDebugPassword
                keyAlias = fixedDebugAlias
                keyPassword = fixedDebugPassword
            }
        }
    } else {
        logger.info("Reelometer: sin llave debug fija; las APK debug se firman con la clave debug del equipo")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            // Solo para probar la build de release en local: ./gradlew assembleRelease -PsignReleaseWithDebug=true
            if (providers.gradleProperty("signReleaseWithDebug").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    lint {
        // Las versiones de AGP y Gradle se suben a propósito, probándolas; no queremos el aviso de «hay una nueva».
        disable += "AndroidGradlePluginVersion"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
