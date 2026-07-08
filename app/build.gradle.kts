plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jack.pushgithub"
    compileSdk = 34  // 降低到 34 避免警告，AGP 8.2.2 兼容

    defaultConfig {
        applicationId = "com.jack.pushgithub"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 启用多 DEX（支持大型项目）
        multiDexEnabled = true
    }


    packagingOptions {
    resources.excludes.add("META-INF/DEPENDENCIES")
    resources.excludes.add("META-INF/LICENSE*")
    resources.excludes.add("META-INF/NOTICE*")
    resources.excludes.add("META-INF/*.properties")
    resources.excludes.add("OSGI-INF/*")
    }


    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = true   // 开启资源缩减和代码优化
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 调试时也建议开启缩减以避免内存问题
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")   // 修复 collectAsStateWithLifecycle
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")  // 包含 FolderOpen 等图标
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.multidex:multidex:2.0.1")     // 多 DEX 支持

    // JGit
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:6.8.0.202311291450-r")
    implementation("org.slf4j:slf4j-simple:1.7.36")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.0")
}
