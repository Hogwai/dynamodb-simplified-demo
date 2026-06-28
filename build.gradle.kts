subprojects {
    apply<JavaPlugin>()

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        add("compileOnly", "org.projectlombok:lombok:${property("lombokVersion")}")
        add("annotationProcessor", "org.projectlombok:lombok:${property("lombokVersion")}")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
