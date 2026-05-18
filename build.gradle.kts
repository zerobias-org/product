import com.zerobias.buildtools.content.SchemaPrimitives

plugins {
    id("zb.workspace")
}

group = "com.zerobias.content"

// ════════════════════════════════════════════════════════════
// Product content validator — owned by this repo.
//
// Philosophy (per Chris/Kevin): the dataloader is the source of truth
// for schema rules (UUID format, code regex, status enum, URL parse,
// vendor/suite lookup, etc.). Re-validating those here just creates
// drift risk — when the dataloader tightens a rule, the gate gets
// stale.
//
// This validator only enforces things the dataloader CANNOT or DOES NOT
// check:
//
//   1. Filesystem ↔ npm ↔ zerobias-block triangulation. Dataloader
//      reads zerobias.package but never the npm `name` field, and has
//      no view of the on-disk directory layout. A wrong npm name
//      publishes under the wrong package and only surfaces in prod.
//
//      Product is unique among content kinds: parentType determines
//      both the directory depth AND the naming formula:
//        parentType: vendor  → package/<v>/<code>/
//                              npm  @zerobias-org/product-<v>-<code>
//                              pkg  <v>.<code>
//        parentType: suite   → package/<v>/<s>/<code>/
//                              npm  @zerobias-org/product-<v>-<s>-<code>
//                              pkg  <v>.<s>.<code>
//      (Source: ProductFileHandler.ts in com/platform/dataloader.)
//
//   2. Logo file correctness. Dataloader doesn't crack open
//      logo.{svg,png,jpg} — it just records the URL. A logo file
//      that's actually an HTML error page (S3 AccessDenied, etc.)
//      ships happily until someone notices.
//
//   3. Repo-wide unique `id` UUIDs (separate :validateUniqueIds task
//      registered below). Dataloader sees one product at a time;
//      collisions only surface when the second one tries to load
//      to the same DB row.
//
// Everything else (UUID validity, URL parse, status enum, code regex,
// factoryTypes enum, vendorId / suiteId lookup, etc.) is delegated to
// the dataloader running in testIntegrationDataloader during gate.
// ════════════════════════════════════════════════════════════
extra["contentValidator"] = { proj: org.gradle.api.Project ->
    val projectDir = proj.projectDir
    val tag = "[product-validator] ${proj.path}"

    require(projectDir.resolve("index.yml").isFile)    { "$tag index.yml missing in ${projectDir.path}" }
    require(projectDir.resolve("package.json").isFile) { "$tag package.json missing in ${projectDir.path}" }
    require(projectDir.resolve(".npmrc").isFile)       { "$tag .npmrc missing in ${projectDir.path}" }

    // ── 1. Filesystem ↔ npm ↔ zerobias-block triangulation ──
    val indexDoc = SchemaPrimitives.parseYaml(projectDir.resolve("index.yml"))
    val parentType = indexDoc["parentType"] as? String
    val productCode = projectDir.name

    val expectedNpmName: String
    val expectedZerobiasPackage: String
    when (parentType) {
        "vendor" -> {
            // package/<vendor>/<product>/
            val vendorCode = projectDir.parentFile.name
            expectedNpmName = "@zerobias-org/product-$vendorCode-$productCode"
            expectedZerobiasPackage = "$vendorCode.$productCode"
            proj.logger.lifecycle("$tag: parentType=vendor vendorCode=$vendorCode code=$productCode")
        }
        "suite" -> {
            // package/<vendor>/<suite>/<product>/
            val suiteCode = projectDir.parentFile.name
            val vendorCode = projectDir.parentFile.parentFile.name
            expectedNpmName = "@zerobias-org/product-$vendorCode-$suiteCode-$productCode"
            expectedZerobiasPackage = "$vendorCode.$suiteCode.$productCode"
            proj.logger.lifecycle("$tag: parentType=suite vendorCode=$vendorCode suiteCode=$suiteCode code=$productCode")
        }
        else -> error(
            "$tag index.yml.parentType must be 'vendor' or 'suite' (got: ${parentType ?: "<missing>"})"
        )
    }

    val pkgDoc = SchemaPrimitives.parseJson(projectDir.resolve("package.json"))
    SchemaPrimitives.requirePackageIdentity(
        pkgDoc,
        expectedNpmName = expectedNpmName,
        expectedZerobiasPackage = expectedZerobiasPackage,
        field = "$tag package.json",
    )

    // ── 2. Logo file correctness ──
    validateLogo(projectDir, pkgDoc, tag)
}

/**
 * Logo file checks the dataloader doesn't perform. Logos are optional —
 * some products ship without one — but when present they must be correct.
 */
fun validateLogo(projectDir: java.io.File, pkgDoc: Map<String, Any?>, tag: String) {
    val candidates = listOf("logo.svg", "logo.png", "logo.jpg")
        .map { projectDir.resolve(it) }
        .filter { it.isFile }

    if (candidates.isEmpty()) return
    require(candidates.size == 1) { "$tag multiple logo files found: ${candidates.joinToString { it.name }}. Keep exactly one." }

    val logo = candidates.single()
    val size = logo.length()
    require(size in 100..(5L * 1024 * 1024)) {
        "$tag ${logo.name} size $size bytes outside acceptable range (100B–5MB) — likely an error response or unscaled asset"
    }

    val head = logo.inputStream().use { stream -> ByteArray(8).also { stream.read(it) } }
    when (logo.extension.lowercase()) {
        "svg" -> {
            val text = String(head).trimStart()
            require(text.startsWith("<?xml") || text.startsWith("<svg") || text.startsWith("<!--")) {
                "$tag ${logo.name} doesn't look like SVG (first bytes: ${head.joinToString(" ") { "%02x".format(it) }}). Possibly an HTML error page."
            }
        }
        "png" -> {
            val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            require(head.contentEquals(pngMagic)) { "$tag ${logo.name} doesn't have the PNG magic bytes." }
        }
        "jpg", "jpeg" -> {
            require(head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()) {
                "$tag ${logo.name} doesn't have the JPEG magic bytes."
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val filesArray = pkgDoc["files"] as? List<String> ?: emptyList()
    val matched = filesArray.any { entry -> entry == logo.name || entry == "logo.*" || entry.startsWith("logo.") }
    require(matched) {
        "$tag package.json files=${filesArray} doesn't include the logo (${logo.name}). Add 'logo.*' (or the exact filename)."
    }
}

// ════════════════════════════════════════════════════════════
// :validateUniqueIds — repo-wide cross-cut.
// Walks every package/**/index.yml, extracts `id`, fails on collision.
// ════════════════════════════════════════════════════════════
val validateUniqueIds by tasks.registering {
    group = "verification"
    description = "Fail if two products share the same index.yml id UUID"

    val packageDir = layout.projectDirectory.dir("package").asFile
    inputs.files(
        fileTree(packageDir) {
            include("**/index.yml")
            exclude("**/node_modules/**")
        }
    )

    doLast {
        val byId = mutableMapOf<String, MutableList<String>>()
        packageDir.walkTopDown()
            .onEnter { it.name != "node_modules" }
            .filter { it.isFile && it.name == "index.yml" }
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
            throw GradleException("[validateUniqueIds] duplicate index.yml ids across the repo:\n$report")
        }
        logger.lifecycle("[validateUniqueIds] ${byId.size} unique ids across ${byId.values.sumOf { it.size }} products")
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
    description = "List products changed since last version tag"
    doLast {
        val lastTag = try {
            providers.exec { commandLine("git", "describe", "--tags", "--abbrev=0") }
                .standardOutput.asText.get().trim()
        } catch (e: Exception) {
            logger.warn("No version tags found -- listing all products as changed")
            null
        }

        val diffArgs = if (lastTag != null) listOf("git", "diff", "--name-only", lastTag, "HEAD")
                       else listOf("git", "ls-files")

        val result = providers.exec { commandLine(diffArgs) }.standardOutput.asText.get()

        // Mixed-depth: walk up each changed file to its nearest
        // build.gradle.kts to know which product it belongs to. The
        // simple "take(N)" trick used by single-depth repos doesn't work.
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
