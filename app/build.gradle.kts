import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val appVersion = (findProperty("appVersion") as String?) ?: "1.0.0"

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val javafxClassifier = when {
    hostOs.contains("mac") -> if (hostArch == "aarch64" || hostArch == "arm64") "mac-aarch64" else "mac"
    hostOs.contains("win") -> "win"
    else -> if (hostArch == "aarch64") "linux-aarch64" else "linux"
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("htmllens")
        browser {
            commonWebpackConfig {
                outputFileName = "htmllens.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        all {
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
            languageSettings.optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            languageSettings.optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons.extended)
            implementation(libs.compose.resources)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ksoup)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.swing)
                // JavaFX WebView (WebKit) renders pages on desktop; natives are per-OS classifiers.
                for (module in listOf("base", "graphics", "controls", "media", "web", "swing")) {
                    implementation("org.openjfx:javafx-$module:${libs.versions.javafx.get()}:$javafxClassifier")
                }
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "app.htmllens.resources"
}

val isMac = System.getProperty("os.name").lowercase().contains("mac")

// JavaFX 26 needs JDK 24+. Run and package the desktop app on Temurin 25 LTS regardless of the JDK running Gradle.
val desktopJavaHome: String = (findProperty("desktopJavaHome") as String?)
    ?: System.getenv("DESKTOP_JAVA_HOME")
    ?: javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }.get().metadata.installationPath.asFile.absolutePath

compose.desktop {
    application {
        mainClass = "app.htmllens.desktop.MainKt"
        javaHome = desktopJavaHome
        jvmArgs += listOf(
            "-Xmx1g",
            "--enable-native-access=ALL-UNNAMED",
        )
        if (isMac) jvmArgs += listOf("-Dapple.awt.application.appearance=system")

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "HTML Lens"
            packageVersion = appVersion
            description = "Open, inspect and safely preview HTML, MHTML, ZIP, EML and Web Archive files."
            vendor = "HTML Lens"
            copyright = "© 2026 HTML Lens"
            licenseFile.set(rootProject.file("LICENSE"))
            modules(
                "java.instrument", "java.management", "java.naming", "java.net.http", "java.sql", "java.scripting", "java.xml",
                "jdk.unsupported", "jdk.unsupported.desktop", "jdk.crypto.ec", "jdk.accessibility", "jdk.httpserver", "jdk.jsobject", "jdk.xml.dom",
            )

            val docTypes = listOf(
                Triple("text/html", "html", "HTML document"),
                Triple("text/html", "htm", "HTML document"),
                Triple("application/xhtml+xml", "xhtml", "XHTML document"),
                Triple("multipart/related", "mhtml", "MHTML web archive"),
                Triple("multipart/related", "mht", "MHT web archive"),
                Triple("application/x-webarchive", "webarchive", "Safari web archive"),
                Triple("message/rfc822", "eml", "E-mail message"),
            )
            for ((mime, ext, desc) in docTypes) {
                fileAssociation(
                    mimeType = mime,
                    extension = ext,
                    description = desc,
                    linuxIconFile = project.file("icons/icon.png"),
                    windowsIconFile = project.file("icons/icon.ico"),
                    macOSIconFile = project.file("icons/icon.icns"),
                )
            }

            macOS {
                bundleID = "app.htmllens.desktop"
                dockName = "HTML Lens"
                appCategory = "public.app-category.developer-tools"
                iconFile.set(project.file("icons/icon.icns"))
                minimumSystemVersion = "11.0"
                // JavaFX extracts its native libraries at runtime; they are signed by a different team.
                entitlementsFile.set(project.file("macos/entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("macos/entitlements.plist"))
                val signId = System.getenv("MACOS_SIGNING_IDENTITY")
                if (!signId.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(signId)
                    }
                    notarization {
                        appleID.set(System.getenv("MACOS_NOTARIZATION_APPLE_ID"))
                        password.set(System.getenv("MACOS_NOTARIZATION_PASSWORD"))
                        teamID.set(System.getenv("MACOS_NOTARIZATION_TEAM_ID"))
                    }
                }
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "HTML Lens"
                upgradeUuid = "6F2C3D9E-4A1B-4C7E-9B21-5D8E7A3C1F40"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
                packageName = "html-lens"
                shortcut = true
            }
        }
    }
}
