plugins {
    java
    idea
    id("opensearch.opensearchplugin")
    id("opensearch.java-agent")
    id("com.netflix.nebula.ospackage")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configureOpensearchPlugin(
    name = project.name,
    description = "Adds rescorer for mixing up search hits inside their groups",
    classname = "company.evo.opensearch.plugin.GroupingMixupPlugin",
    numberOfTestClusterNodes = 2,
    // Compatibility between MINOR update is not working at the moment:
    // https://github.com/opensearch-project/OpenSearch/issues/18787
    opensearchCompatibility = OpensearchCompatibility.REVISION,
)

version = Versions.project
