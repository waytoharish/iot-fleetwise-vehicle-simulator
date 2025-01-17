import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {   
    application
    kotlin("jvm") version ("1.9.0")
}

java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
  mavenCentral()
}

dependencies {                              
    implementation("org.slf4j:slf4j-log4j12:1.7.29")
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    implementation("info.picocli:picocli:4.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:1.0.9")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.0.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation(platform("software.amazon.awssdk:bom:2.20.56"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:acmpca")
    implementation("software.amazon.awssdk:ecs")
    implementation("software.amazon.awssdk:ssooidc")
    implementation("software.amazon.awssdk:acmpca")
    implementation("software.amazon.awssdk:iot")
    implementation("software.amazon.awssdk:iotfleetwise")
    implementation("software.amazon.awssdk:iam")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
     // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("info.picocli:picocli:4.5.2")
    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

sourceSets {                                
    main {               
        kotlin { 
            setSrcDirs(listOf("src/main/kotlin"))
        }
        resources {
            setSrcDirs(listOf("src/main/resources"))
        }
    }
}

application {
    // Define the main class for the application.
    mainClass.set("com.amazonaws.iot.fleetwise.vehiclesimulator.cli.VehicleSimulatorCommandKt")
}
