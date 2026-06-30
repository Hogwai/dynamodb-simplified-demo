plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Spring Boot demo for DynamoDB Simplified"

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("org.jspecify:jspecify:${property("jspecifyVersion")}")

    runtimeOnly("ch.qos.logback:logback-classic")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${property("testcontainersVersion")}")
}
