plugins {
    id("org.springframework.boot")
}

springBoot {
    mainClass.set("pyc.lopatuxin.App")
}

val testcontainersVersion = "1.20.4"

dependencies {
    implementation(project(":shared"))
    implementation(project(":auth"))
    implementation(project(":budget"))
    implementation(project(":investment"))
    implementation(project(":security"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-liquibase")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Fixed, deterministic boot-jar name (Dockerfile relies on it instead of a glob).
// The plain (non-executable) jar isn't needed, so its task is disabled entirely.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
tasks.named<Jar>("jar") { enabled = false }
