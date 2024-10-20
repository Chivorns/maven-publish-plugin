// Helper method to load properties from multiple files
fun loadPropertiesFromFiles() {
    val secretPropsFile = project.rootProject.file("local.properties")
    val credentialPropsFile = project.rootProject.file("credential.properties")

    val properties = Properties()

    // First load from local.properties
    if (secretPropsFile.exists()) {
        secretPropsFile.inputStream().use { stream ->
            properties.load(stream)
        }
    }

    // Then load from credential.properties (this can overwrite properties from local.properties)
    if (credentialPropsFile.exists()) {
        credentialPropsFile.inputStream().use { stream ->
            properties.load(stream)
        }
    }

    // Store properties in project.ext so they can be accessed globally
    properties.forEach { name, value -> extra[name.toString()] = value }
}

// Helper method to fetch required properties
fun getRequiredProperty(propertyName: String): String {
    if (!project.hasProperty(propertyName)) {
        throw GradleException("Missing required property: $propertyName")
    }
    return project.property(propertyName) as String
}

// Root-level Nexus publishing setup
if (project == rootProject) {
    loadPropertiesFromFiles()

    nexusPublishing {
        repositories {
            sonatype {
                stagingProfileId.set(getRequiredProperty("sonatypeStagingProfileId"))
                username.set(getRequiredProperty("publish.username"))
                password.set(getRequiredProperty("publish.password"))
            }
        }
    }
} else {
    // Apply Maven Publish and Signing plugins
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    // Register the Android sources JAR task
    tasks.register<Jar>("androidSourcesJar") {
        if (project.plugins.hasPlugin("com.android.library")) {
            from(android.sourceSets["main"].java.srcDirs)
        } else {
            from(sourceSets["main"].java.srcDirs)
        }
    }

    // Use project-specific properties for group, artifactId, and version
    group = getRequiredProperty("PUBLISH_GROUP_ID")
    version = getRequiredProperty("PUBLISH_VERSION")

    val artifactId = getRequiredProperty("PUBLISH_ARTIFACT_ID")

    afterEvaluate {
        publishing {
            publications {
                create<MavenPublication>("release") {
                    this.artifactId = artifactId
                    this.groupId = group
                    this.version = version

                    // Add components based on module type
                    if (project.plugins.hasPlugin("com.android.library")) {
                        from(components["release"])
                    } else {
                        artifact("$buildDir/libs/${project.name}-${version}.jar")
                    }

                    // Add sources JAR
                    artifact(tasks.named<Jar>("androidSourcesJar"))

                    // Configure POM metadata
                    pom {
                        name.set(artifactId)
                        description.set(getRequiredProperty("PUBLISH_DESCRIPTION"))
                        url.set(getRequiredProperty("PUBLISH_URL"))

                        licenses {
                            license {
                                name.set(getRequiredProperty("PUBLISH_LICENSE_NAME"))
                                // Uncomment if required
                                // url.set(getRequiredProperty("PUBLISH_LICENSE_URL"))
                            }
                        }
                        developers {
                            developer {
                                id.set(getRequiredProperty("PUBLISH_DEVELOPER_ID"))
                                name.set(getRequiredProperty("PUBLISH_DEVELOPER_NAME"))
                                email.set(getRequiredProperty("PUBLISH_DEVELOPER_EMAIL"))
                            }
                        }
                        scm {
                            connection.set(getRequiredProperty("PUBLISH_SCM_CONNECTION"))
                            developerConnection.set(getRequiredProperty("PUBLISH_SCM_DEVELOPER_CONNECTION"))
                            url.set(getRequiredProperty("PUBLISH_SCM_URL"))
                        }
                    }
                }
            }
        }

        // Automatically sign publications
        signing {
            sign(publishing.publications["release"])
        }
    }
}