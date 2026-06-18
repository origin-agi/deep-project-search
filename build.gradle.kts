import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

val signingPropertiesFile = layout.projectDirectory.file("signing/signing.properties").asFile
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

plugins {
    java
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)
        zipSigner()
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            current()
            create("WS", "2025.3.5")
        }
    }

    signing {
        certificateChainFile.set(layout.projectDirectory.file("signing/chain.crt"))
        privateKeyFile.set(layout.projectDirectory.file("signing/private.pem"))
        password.set(signingProperties.getProperty("password"))
    }
}

tasks.named("verifyPluginSignature") {
    dependsOn("signPlugin")
}
