import org.gradle.api.tasks.testing.Test

plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.peatroxd"
version = "0.0.1-SNAPSHOT"
description = "StreamCutProject"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val e2eTest by sourceSets.creating {
    java.srcDir("src/e2eTest/java")
    resources.srcDir("src/e2eTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[e2eTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[e2eTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
configurations[e2eTest.compileOnlyConfigurationName].extendsFrom(configurations.testCompileOnly.get())
configurations[e2eTest.annotationProcessorConfigurationName].extendsFrom(configurations.testAnnotationProcessor.get())

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(platform("software.amazon.awssdk:bom:2.32.10"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3-transfer-manager")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "e2eTestImplementation"("com.codeborne:selenide:7.15.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val forwardedE2eProperties = listOf(
    "selenide.baseUrl",
    "selenide.browser",
    "selenide.browserSize",
    "selenide.headless",
    "selenide.timeout",
    "e2e.username",
    "e2e.password"
)

tasks.register<Test>("e2eTest") {
    description = "Runs browser end-to-end tests with Selenide."
    group = "verification"
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/e2eTests"))
    systemProperties(
        forwardedE2eProperties.mapNotNull { propertyName ->
            System.getProperty(propertyName)?.let { propertyName to it }
        }.toMap()
    )
}
