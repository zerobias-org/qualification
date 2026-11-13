import com.zerobias.buildtools.content.SchemaPrimitives
import java.security.MessageDigest
import java.util.UUID

plugins {
    id("zb.workspace")
}

group = "com.zerobias.content"

// ════════════════════════════════════════════════════════════
// Qualification content validator — owned by this repo.
//
// The dataloader is the source of truth for schema rules. This validator
// only enforces what the dataloader cannot check:
//
//   1. Filesystem ↔ npm ↔ zerobias-block triangulation:
//        dir              = package/<issuer>/<code>/
//        npm name         = @zerobias-org/qualification-<issuer>-<code>
//        zerobias.package = <issuer>.<code>.qualification
//
//   2. index.yml `id` = UUIDv5(nil namespace, `code`), the rule the
//      dataloader's role artifact applies to catalog qualifications.
//
//   3. The directory's <issuer> is listed in index.yml `vendors`.
//
//   4. Repo-wide unique `id` UUIDs across every *.yml under package/.
// ════════════════════════════════════════════════════════════

fun uuidV5Nil(name: String): UUID {
    val sha1 = MessageDigest.getInstance("SHA-1")
    sha1.update(ByteArray(16))
    val hash = sha1.digest(name.toByteArray(Charsets.UTF_8))
    hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
    hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
    var msb = 0L
    var lsb = 0L
    for (i in 0 until 8) msb = (msb shl 8) or (hash[i].toLong() and 0xff)
    for (i in 8 until 16) lsb = (lsb shl 8) or (hash[i].toLong() and 0xff)
    return UUID(msb, lsb)
}

extra["contentValidator"] = { proj: org.gradle.api.Project ->
    val projectDir = proj.projectDir
    val tag = "[qualification-validator] ${proj.path}"

    require(projectDir.resolve("index.yml").isFile)    { "$tag index.yml missing in ${projectDir.path}" }
    require(projectDir.resolve("package.json").isFile) { "$tag package.json missing in ${projectDir.path}" }
    require(projectDir.resolve(".npmrc").isFile)       { "$tag .npmrc missing in ${projectDir.path}" }

    // ── 1. Filesystem ↔ npm ↔ zerobias-block triangulation ──
    val code = projectDir.name
    val issuer = projectDir.parentFile.name

    val pkgDoc = SchemaPrimitives.parseJson(projectDir.resolve("package.json"))
    SchemaPrimitives.requirePackageIdentity(
        pkgDoc,
        expectedNpmName = "@zerobias-org/qualification-$issuer-$code",
        expectedZerobiasPackage = "$issuer.$code.qualification",
        field = "$tag package.json",
    )
    require(SchemaPrimitives.getPath(pkgDoc, "zerobias.import-artifact") == "qualification" ||
            SchemaPrimitives.getPath(pkgDoc, "auditmation.import-artifact") == "qualification") {
        "$tag zerobias.import-artifact must be 'qualification'"
    }

    // ── 2. Deterministic id ──
    val index = SchemaPrimitives.parseYaml(projectDir.resolve("index.yml"))
    val qualCode = index["code"] as? String
        ?: throw GradleException("$tag index.yml `code` is missing")
    val expectedId = uuidV5Nil(qualCode).toString()
    require((index["id"] as? String)?.lowercase() == expectedId) {
        "$tag index.yml id must be UUIDv5(nil, \"$qualCode\") = $expectedId"
    }

    // ── 3. Issuer directory is a listed vendor ──
    val vendors = (index["vendors"] as? List<*>)?.map { it.toString() } ?: emptyList()
    require(issuer in vendors) {
        "$tag directory issuer '$issuer' must appear in index.yml vendors $vendors"
    }

    proj.logger.lifecycle("$tag: issuer=$issuer code=$code ($qualCode)")
}

// ════════════════════════════════════════════════════════════
// :validateUniqueIds — repo-wide cross-cut over all *.yml.
// ════════════════════════════════════════════════════════════
val validateUniqueIds by tasks.registering {
    group = "verification"
    description = "Fail if two qualification YAMLs share the same id UUID"

    val packageDir = layout.projectDirectory.dir("package").asFile
    inputs.files(
        fileTree(packageDir) {
            include("**/*.yml")
            exclude("**/node_modules/**")
        }
    )

    doLast {
        val byId = mutableMapOf<String, MutableList<String>>()
        packageDir.walkTopDown()
            .onEnter { it.name != "node_modules" }
            .filter { it.isFile && it.name.endsWith(".yml") }
            .forEach { f ->
                val doc = try {
                    SchemaPrimitives.parseYaml(f)
                } catch (e: Exception) {
                    logger.warn("[validateUniqueIds] skipping unparseable ${f.relativeTo(rootDir)}: ${e.message}")
                    return@forEach
                }
                val id = (doc["id"] as? String)?.lowercase() ?: return@forEach
                byId.getOrPut(id) { mutableListOf() }.add(f.relativeTo(rootDir).path)
            }

        val collisions = byId.filterValues { it.size > 1 }
        if (collisions.isNotEmpty()) {
            val report = collisions.entries.joinToString("\n") { (id, paths) ->
                "  $id\n    " + paths.joinToString("\n    ")
            }
            throw GradleException("[validateUniqueIds] duplicate qualification ids across the repo:\n$report")
        }
        logger.lifecycle("[validateUniqueIds] ${byId.size} unique ids across ${byId.values.sumOf { it.size }} yaml files")
    }
}

subprojects {
    tasks.matching { it.name == "validateContent" }.configureEach {
        dependsOn(rootProject.tasks.named("validateUniqueIds"))
    }
}

val projectPaths by tasks.registering {
    group = "info"
    description = "Output project-to-directory mappings for tooling (used by zbb CLI)"
    doLast {
        subprojects.filter { it.buildFile.exists() }.forEach { p ->
            println("${p.path}=${p.projectDir.relativeTo(rootDir)}")
        }
    }
}

val changedModules by tasks.registering {
    group = "info"
    description = "List qualification packages changed since last version tag"
    doLast {
        val lastTag = try {
            providers.exec { commandLine("git", "describe", "--tags", "--abbrev=0") }
                .standardOutput.asText.get().trim()
        } catch (e: Exception) {
            logger.warn("No version tags found -- listing all qualification packages as changed")
            null
        }

        val diffArgs = if (lastTag != null) listOf("git", "diff", "--name-only", lastTag, "HEAD")
                       else listOf("git", "ls-files")

        val result = providers.exec { commandLine(diffArgs) }.standardOutput.asText.get()

        val packageDir = rootDir.resolve("package")
        val changed = mutableSetOf<String>()
        result.lines()
            .filter { it.startsWith("package/") }
            .forEach { line ->
                var dir = rootDir.resolve(line).parentFile
                while (dir != null && dir != packageDir && dir.startsWith(packageDir)) {
                    if (dir.resolve("build.gradle.kts").isFile) {
                        changed.add(dir.relativeTo(packageDir).path)
                        break
                    }
                    dir = dir.parentFile
                }
            }
        changed.forEach { println(it) }
    }
}
