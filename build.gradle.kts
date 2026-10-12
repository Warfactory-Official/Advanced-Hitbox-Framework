import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("dev.prism")
}

fun org.gradle.api.artifacts.repositories.MavenArtifactRepository.warfactoryCredentials() {
    val user = (project.findProperty("warfactoryUser") as String?) ?: System.getenv("WARFACTORY_MAVEN_USER")
    val token = (project.findProperty("warfactoryToken") as String?) ?: System.getenv("WARFACTORY_MAVEN_TOKEN")
    // Anonymous reads work on this server; only `publish` needs credentials. A present-but-null
    // credentials block makes Gradle refuse anonymous reads too, so omit it entirely when unset.
    if (user != null && token != null) {
        credentials {
            username = user
            password = token
        }
    }
}

group = "com.norwood"
version = "1.0.8"

prism {
    metadata {
        modId = "ahf"
        name = "Advanced Hitbox Framework"
        description = "Adds proper hitboxes into the game"
        license = "MIT"
    }

    maven("CurseMaven", "https://www.cursemaven.com")
    maven("KosmX", "https://maven.kosmx.dev/")

    version("1.20.1") {
        forge {
            loaderVersion = "47.4.18"
            loaderVersionRange = "[47,)"
            dependencies {
                modCompileOnly("curse.maven:timeless-and-classics-zero-1028108:8141310")
                modCompileOnly("dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20")
                compileOnly("io.github.llamalad7:mixinextras-common:0.4.1")
                annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1")
                jarJar("io.github.llamalad7:mixinextras-forge:0.4.1")
                configuration("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.3")
                configuration("testImplementation", "dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20")
                configuration("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.11.3")
            }
            rawProject {
                val sourceSets = extensions.getByType<SourceSetContainer>()
                val main = sourceSets.getByName("main")
                val gametest = sourceSets.create("gametest") {
                    compileClasspath += main.output
                    runtimeClasspath += main.output
                }
                configurations.named(gametest.compileClasspathConfigurationName) {
                    extendsFrom(configurations.getByName(main.compileClasspathConfigurationName))
                }
                configurations.named(gametest.runtimeClasspathConfigurationName) {
                    extendsFrom(configurations.getByName(main.runtimeClasspathConfigurationName))
                }
                repositories {
                    maven {
                        name = "WarfactorySnapshots"
                        url = uri("https://repo.warfactory.co/snapshots")
                        warfactoryCredentials()
                        content { includeGroup("com.wf.gemrender") }
                    }
                }
                configurations.all {
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
                dependencies.add("compileOnly", "com.wf.gemrender:gemrender-1.20.1:0.1.16-SNAPSHOT")
                dependencies.add("testImplementation", "com.wf.gemrender:gemrender-1.20.1:0.1.16-SNAPSHOT")
                val forge = extensions.getByType<net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension>()
                forge.addModdingDependenciesTo(gametest)
                forge.addModdingDependenciesTo(sourceSets.getByName("test"))
                forge.mods.getByName("ahf").sourceSet(gametest)
                forge.runs.create("gameTestServer") {
                    type.set("gameTestServer")
                    gameDirectory.set(file("runs/gametest"))
                    systemProperty("forge.enabledGameTestNamespaces", "ahf")
                }
                tasks.named<Test>("test") {
                    useJUnitPlatform()
                }
                apply(plugin = "maven-publish")
                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                            groupId = "com.norwood"
                            artifactId = "ahf-1.20.1-forge"
                            version = "1.0.8-SNAPSHOT"
                        }
                    }
                    repositories {
                        maven {
                            name = "WarfactorySnapshots"
                            url = uri("https://repo.warfactory.co/snapshots")
                            warfactoryCredentials()
                        }
                    }
                }
            }
        }
    }

    version("1.21.1") {
        neoforge {
            loaderVersion = "21.1.222"
            loaderVersionRange = "[4,)"
            dependencies {
                compileOnly("dev.kosmx.player-anim:player-animation-lib-forge:2.0.4+1.21.1")
                configuration("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.3")
                configuration("testImplementation", "dev.kosmx.player-anim:player-animation-lib-forge:2.0.4+1.21.1")
                configuration("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.11.3")
            }
            rawProject {
                val sourceSets = extensions.getByType<SourceSetContainer>()
                val main = sourceSets.getByName("main")
                val gametest = sourceSets.create("gametest") {
                    compileClasspath += main.output
                    runtimeClasspath += main.output
                }
                configurations.named(gametest.compileClasspathConfigurationName) {
                    extendsFrom(configurations.getByName(main.compileClasspathConfigurationName))
                }
                configurations.named(gametest.runtimeClasspathConfigurationName) {
                    extendsFrom(configurations.getByName(main.runtimeClasspathConfigurationName))
                }
                repositories {
                    maven {
                        name = "WarfactorySnapshots"
                        url = uri("https://repo.warfactory.co/snapshots")
                        warfactoryCredentials()
                        content { includeGroup("com.wf.gemrender") }
                    }
                }
                configurations.all {
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
                dependencies.add("compileOnly", "com.wf.gemrender:gemrender-1.21.1:0.1.16-SNAPSHOT")
                dependencies.add("testImplementation", "com.wf.gemrender:gemrender-1.21.1:0.1.16-SNAPSHOT")
                val neoForge = extensions.getByType<NeoForgeExtension>()
                neoForge.addModdingDependenciesTo(gametest)
                val mod = neoForge.mods.getByName("ahf")
                mod.sourceSet(gametest)
                neoForge.unitTest {
                    enable()
                    testedMod.set(mod)
                }
                neoForge.runs.create("gameTestServer") {
                    type.set("gameTestServer")
                    gameDirectory.set(file("runs/gametest"))
                    systemProperty("neoforge.enabledGameTestNamespaces", "ahf")
                }
                tasks.named<Test>("test") {
                    useJUnitPlatform()
                }
                apply(plugin = "maven-publish")
                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                            groupId = "com.norwood"
                            artifactId = "ahf-1.21.1-neoforge"
                            version = "1.0.8-SNAPSHOT"
                        }
                    }
                    repositories {
                        maven {
                            name = "WarfactorySnapshots"
                            url = uri("https://repo.warfactory.co/snapshots")
                            warfactoryCredentials()
                        }
                    }
                }
            }
        }
    }

    version("26.1") {
        neoforge {
            loaderVersion = "26.1.1.0-beta"
            loaderVersionRange = "[4,)"
            dependencies {
                configuration("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.3")
                configuration("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.11.3")
            }
            rawProject {
                val sourceSets = extensions.getByType<SourceSetContainer>()
                val main = sourceSets.getByName("main")
                val gametest = sourceSets.create("gametest") {
                    compileClasspath += main.output
                    runtimeClasspath += main.output
                }
                configurations.named(gametest.compileClasspathConfigurationName) {
                    extendsFrom(configurations.getByName(main.compileClasspathConfigurationName))
                }
                configurations.named(gametest.runtimeClasspathConfigurationName) {
                    extendsFrom(configurations.getByName(main.runtimeClasspathConfigurationName))
                }
                val neoForge = extensions.getByType<NeoForgeExtension>()
                neoForge.addModdingDependenciesTo(gametest)
                val mod = neoForge.mods.getByName("ahf")
                mod.sourceSet(gametest)
                neoForge.unitTest {
                    enable()
                    testedMod.set(mod)
                }
                neoForge.runs.create("gameTestServer") {
                    type.set("gameTestServer")
                    gameDirectory.set(file("runs/gametest"))
                    programArguments.addAll("--tests", "ahf:*")
                }
                tasks.named<Test>("test") {
                    useJUnitPlatform()
                }
                apply(plugin = "maven-publish")
                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                            groupId = "com.norwood"
                            artifactId = "ahf-26.1-neoforge"
                            version = "1.0.8-SNAPSHOT"
                        }
                    }
                    repositories {
                        maven {
                            name = "WarfactorySnapshots"
                            url = uri("https://repo.warfactory.co/snapshots")
                            warfactoryCredentials()
                        }
                    }
                }
            }
        }
    }

}
