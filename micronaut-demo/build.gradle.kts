plugins {
    id("io.micronaut.minimal.application")
    id("com.gradleup.shadow")
    id("io.micronaut.aot")
}

description = "Micronaut demo for DynamoDB Simplified"

dependencies {
    implementation(project(":common"))
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.aws:micronaut-aws-sdk-v2")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("net.datafaker:datafaker:${property("datafakerVersion")}")

    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")

    runtimeOnly("ch.qos.logback:logback-classic")

    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("io.projectreactor:reactor-test")
}

application {
    mainClass = "dev.hogwai.micronaut.Application"
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("dev.hogwai.*")
    }
    aot {
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = false
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}
