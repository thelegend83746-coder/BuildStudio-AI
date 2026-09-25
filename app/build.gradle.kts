plugins {
    id("com.android.application")
}

android {
    namespace = "com.build.studio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.build.studio"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"
        multiDexEnabled = true

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "**"
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.multidex:multidex:2.0.1")

    // Sora Editor
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.4")
    implementation("io.github.Rosemoe.sora-editor:language-java:0.23.4")

    // HTTP / REST API Client for Build AI (Ollama & OpenAI endpoints)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Compiler Toolchain libraries (ECJ, D8/R8)
    implementation("org.eclipse.jdt:ecj:3.26.0")
    implementation("com.android.tools:r8:8.2.33")

    compileOnly(files("libs/cp.jar"))
    implementation(files("libs/apksig-8.2.2.jar"))
    implementation(files("src/main/assets/libs/androidx-stubs.jar"))
}
