rootProject.name = "dynamodb-simplified-demo"

pluginManagement {
    plugins {
        id("io.micronaut.minimal.application") version "4.6.1"
        id("io.micronaut.aot") version "4.6.1"
        id("com.gradleup.shadow") version "8.3.9"
        id("org.springframework.boot") version "4.1.0"
        id("io.spring.dependency-management") version "1.1.7"
    }
}

include("common")
include("micronaut-demo")
include("spring-boot-demo")
