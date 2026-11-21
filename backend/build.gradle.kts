plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.10"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ar.turnosodontologia"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.12.0"))
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xpkginfo:always"))
}

tasks.bootRun {
    jvmArgs("-Duser.timezone=UTC")
}

// Archivos fuera de backend/ que los tests leen (script de roles y plantilla de entorno): se declaran como
// entradas para que un cambio en ellos vuelva a ejecutar los tests en lugar de dejarlos UP-TO-DATE.
val filesReadByTests = files("../infra/postgres/init", "../.env.example")

tasks.test {
    useJUnitPlatform()
    exclude("**/*IT.class")
    jvmArgs("-Duser.timezone=UTC")
    inputs.files(filesReadByTests).withPropertyName("filesReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (*IT) against an embedded PostgreSQL (zonky). No installation required."
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*IT.class")
    jvmArgs("-Duser.timezone=UTC")
    inputs.files(filesReadByTests).withPropertyName("filesReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData.setFrom(
        layout.buildDirectory.files("jacoco/test.exec", "jacoco/integrationTest.exec")
            .filter { it.exists() }
    )
    reports {
        xml.required = true
        html.required = true
    }
}
