import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.cloudgaming"
version = "0.0.1-SNAPSHOT"
description = "user-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"] = "2025.1.2"
extra["testcontainersVersion"] = "1.20.4"
extra["redissonVersion"] = "4.6.0"
extra["resilience4jVersion"] = "2.2.0"
extra["springdocVersion"] = "2.8.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework:spring-aop")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    implementation("org.springframework.kafka:spring-kafka")

    implementation("org.redisson:redisson-spring-boot-starter:${property("redissonVersion")}")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    implementation("io.github.resilience4j:resilience4j-spring-boot3:${property("resilience4jVersion")}")
    implementation("io.github.resilience4j:resilience4j-reactor:${property("resilience4jVersion")}")

    implementation("org.keycloak:keycloak-admin-client:24.0.4")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    testImplementation(enforcedPlatform("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}"))
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.3.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property"
        )
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

kover {
    reports {
        filters {
            excludes {
                packages("com.cloudgaming.userservice.exception")
                packages("com.cloudgaming.userservice.domain")
                packages("com.cloudgaming.userservice.config")
                packages("com.cloudgaming.userservice.constants")
                packages("com.cloudgaming.userservice.dto")
                classes("com.cloudgaming.userservice.UserServiceApplicationKt")
            }
        }

        verify {
            rule {
                minBound(70)
            }
        }
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("TESTCONTAINERS_RYUK_DISABLED", "false")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    args("--spring.profiles.active=dev")
}