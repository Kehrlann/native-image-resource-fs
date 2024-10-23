plugins {
    id("java")
    id("org.graalvm.buildtools.native") version "0.10.3"
}

group = "wf.garnier.native-image"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

graalvmNative {
    binaries {
        all {
            buildArgs("-Ob")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}