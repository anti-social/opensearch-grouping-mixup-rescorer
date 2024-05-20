import java.nio.file.Paths

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath("org.opensearch.gradle:build-tools:${Versions.opensearch}")
    }
}

plugins {
    java
    idea
    id("opensearch.opensearchplugin")
    id("opensearch.testclusters")
    id("com.netflix.nebula.ospackage") version "11.9.0"
}

apply {
    plugin("opensearch.opensearchplugin")
    plugin("opensearch.testclusters")
}

val versions = org.opensearch.gradle.VersionProperties.getVersions() as Map<String, String>
val pluginName = "grouping-mixup-rescorer"

configure<org.opensearch.gradle.plugin.PluginPropertiesExtension> {
    name = pluginName
    description = "Adds rescorer for mixing up search hits inside their groups."
    classname = "company.evo.opensearch.plugin.GroupingMixupPlugin"
    version = Versions.plugin
}

configure<NamedDomainObjectContainer<org.opensearch.gradle.testclusters.OpenSearchCluster>> {
    val integTestCluster = create("integTest") {
        setTestDistribution(org.opensearch.gradle.testclusters.TestDistribution.INTEG_TEST)
        plugin(tasks.named<Zip>("bundlePlugin").get().archiveFile)
    }

    val integTestTask = tasks.register<org.opensearch.gradle.test.RestIntegTestTask>("integTest") {
        dependsOn("bundlePlugin")
    }

    tasks.named("check") {
        dependsOn(integTestTask)
    }
}

tasks.register("listRepos") {
    doLast {
        println("Repositories:")
        project.repositories.forEach {
            print("- ")
            if (it is MavenArtifactRepository) {
                println("Name: ${it.name}; url: ${it.url}")
            } else if (it is IvyArtifactRepository) {
                println("Name: ${it.name}; url: ${it.url}")
            } else {
                println("Unknown repository type: $it")
            }
        }
    }
}

setProperty("licenseFile", project.rootProject.file("LICENSE.txt"))
setProperty("noticeFile", project.rootProject.file("NOTICE.txt"))

version = Versions.project

val distDir = Paths.get(project.layout.buildDirectory.toString(), "distributions")

tasks.register("deb", com.netflix.gradle.plugins.deb.Deb::class) {
    dependsOn("bundlePlugin")

    packageName = project.name
    requires("opensearch", versions["opensearch"])

    from(zipTree(tasks["bundlePlugin"].outputs.files.singleFile))

    val esHome = project.properties["opensearchHome"] ?: "/usr/share/opensearch"
    into("$esHome/plugins/${pluginName}")

    doLast {
        if (properties.containsKey("assembledInfo")) {
            distDir.resolve("assembled-deb.filename").toFile()
                .writeText(assembleArchiveName())
        }
    }
}
tasks.named("assemble") {
    dependsOn("deb")
}
