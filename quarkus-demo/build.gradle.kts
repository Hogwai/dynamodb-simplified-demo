plugins {
    java
    id("io.quarkus")
}

description = "Quarkus demo for DynamoDB Simplified"

tasks.named("test") {
    dependsOn("quarkusBuild")
}

dependencies {
    implementation(project(":common"))
    implementation("dev.hogwai:dynamodb-simplified-core:0.1.0")
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.2.1"))
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-mutiny")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-test-security")
    testImplementation("org.testcontainers:testcontainers:1.20.6")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
}
