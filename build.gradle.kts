plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.draken"
version = "1.0.3"

kotlin {
    jvmToolchain(11)
}

tasks.jar {
    exclude("android/**")
    exclude("androidx/annotation/**")
    exclude("androidx/preference/**")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=tsuki.InternalParsersApi",
        )
    }
}

tasks.matching {
    it.name in setOf("compileTestKotlin", "compileTestJava", "processTestResources", "testClasses", "test")
}.configureEach {
    enabled = false
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}
	repositories {
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/UsagiApp/Tsuki")
			credentials {
				username = System.getenv("GITHUB_ACTOR") ?: ""
				password = System.getenv("GITHUB_TOKEN") ?: ""
			}
		}
	}
}

dependencies {
	implementation(libs.androidx.collection)
	api(libs.jsoup)
	implementation(libs.json)
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.okhttp)
	implementation(libs.okio)

	testImplementation(libs.junit.api)
	testImplementation(libs.junit.engine)
	testImplementation(libs.junit.launcher)
	testImplementation(libs.junit.params)
	testImplementation(libs.kotlinx.coroutines.test)
}
