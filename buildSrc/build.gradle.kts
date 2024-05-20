import java.nio.file.Paths
import java.util.Properties

plugins {
    `kotlin-dsl`
    idea
    id("org.ajoberstar.grgit") version "4.1.0"
}

val defaultOpensearchVersion = readVersion("opensearch-default.version")

val gitDescribe = grgit.describe(mapOf("match" to listOf("v*-opensearch*"), "tags" to true))
    ?: "v0.0.0-opensearch$defaultOpensearchVersion"

class GitDescribe(val describe: String) {
    private val VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+(\\-(alpha|beta|rc)\\-[0-9]+)?"

    private val matchedGroups =
        "v(?<plugin>${VERSION_REGEX})-opensearch(?<opensearch>${VERSION_REGEX})(-(?<abbrev>.*))?".toRegex()
            .matchEntire(describe)!!
            .groups

    val plugin = matchedGroups["plugin"]!!.value
    val opensearch = matchedGroups["opensearch"]!!.value
    val abbrev = matchedGroups["abbrev"]?.value

    fun opensearchVersion() = if (hasProperty("opensearchVersion")) {
        property("opensearchVersion")
    } else {
        // When adopting to new OpenSearch version
        // create `buildSrc/es.version` file so IDE can fetch correct version of OpenSearch
        readVersion("opensearch.version") ?: opensearch
    }

    fun pluginVersion() = buildString {
        append(plugin)
        if (abbrev != null) {
            append("-$abbrev")
        }
    }

    fun projectVersion() = buildString {
        append("$plugin-opensearch${opensearchVersion()}")
        if (abbrev != null) {
            append("-$abbrev")
        }
    }
}
val describe = GitDescribe(gitDescribe)

val generatedResourcesDir = Paths.get(buildDir.path, "generated-resources", "main")

sourceSets {
    main {
        output.dir(mapOf("builtBy" to "generateVersionProperties"), generatedResourcesDir)
    }
}

tasks.create("generateVersionProperties") {
    outputs.dir(generatedResourcesDir)
    doLast {
        val versionProps = Properties().apply {
            put("tag", describe.describe)
            put("projectVersion", describe.projectVersion())
            put("pluginVersion", describe.pluginVersion())
            put("opensearchVersion", describe.opensearchVersion())
        }
        generatedResourcesDir.resolve("opensearch-plugin-versions.properties").toFile().writer().use {
            versionProps.store(it, null)
        }
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

repositories {
    mavenLocal()
    gradlePluginPortal()
}

idea {
    module {
        isDownloadJavadoc = false
        isDownloadSources = false
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32")
    implementation("org.opensearch.gradle:build-tools:${describe.opensearchVersion()}")
}

// Utils

fun readVersion(fileName: String): String? {
    project.projectDir.toPath().resolve(fileName).toFile().let {
        if (it.exists()) {
            val opensearchVersion = it.readText().trim()
            if (!opensearchVersion.startsWith('#')) {
                return opensearchVersion
            }
            return null
        }
        return null
    }
}
