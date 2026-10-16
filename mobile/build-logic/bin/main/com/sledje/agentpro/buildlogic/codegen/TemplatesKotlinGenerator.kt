package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generates `Templates.kt` (package com.sledje.agentpro.core.catalog) from `shared/templates/index.json`:
 * a `TemplateSpec` per template (key, translated name and description, supported section keys, the
 * default accent as an `AccentSwatch`, preview path, order) and `Templates.all` in display order.
 * The onboarding and Website template pickers read it, so template data is never hard-coded in Kotlin.
 */
class TemplatesKotlinGenerator(private val defs: SharedDefinitions) {

    fun generate(): String {
        val swatches = defs.catalogList("accentSwatches")
        val templates = (defs.templates["templates"] as? JsonArray).orEmpty()
            .map { it as? JsonObject ?: error("templates/index.json: every template must be an object") }
            .sortedBy { it.long("order") ?: Long.MAX_VALUE }
        val w = CodeWriter()
        w.raw(Naming.GENERATED_HEADER)
        w.line("@file:Suppress(\"unused\")")
        w.line()
        w.line("package ${Naming.CATALOG_PACKAGE}")
        w.line()
        w.line("import ${Naming.RES_PACKAGE}.*")
        w.line("import org.jetbrains.compose.resources.StringResource")
        w.line()
        w.line("/** One website template of `shared/templates/index.json`. */")
        w.line("data class TemplateSpec(")
        w.indent {
            line("val key: String,")
            line("val name: StringResource,")
            line("val description: StringResource,")
            line("/** Section keys of site-schema.json this template renders. */")
            line("val sections: List<String>,")
            line("val defaultAccent: AccentSwatch,")
            line("/** Preview image path, relative to the repo root (web/templates/<key>/preview.webp). */")
            line("val preview: String,")
            line("val order: Int,")
        }
        w.line(")")
        w.line()
        w.line("/** `shared/templates/index.json`: the website templates, in display order. */")
        w.line("object Templates {")
        w.indent {
            line("const val VERSION: Int = ${defs.templates.long("version") ?: 0}")
            line()
            line("val all: List<TemplateSpec> = listOf(")
            indent { templates.forEach { writeTemplate(this, it, swatches) } }
            line(")")
            line()
            line("fun fromKey(key: String): TemplateSpec? = all.firstOrNull { it.key == key }")
        }
        w.line("}")
        return w.toString()
    }

    private fun writeTemplate(w: CodeWriter, t: JsonObject, swatches: CatalogList?) {
        val key = t.string("key") ?: error("templates/index.json: template without key")
        val accent = t.string("defaultAccent") ?: error("template $key has no defaultAccent")
        require(swatches != null && accent in swatches.keys) { "template $key: defaultAccent \"$accent\" is not an accentSwatches key" }
        val sections = t.array("sections").orEmpty().map { (it as? JsonPrimitive)?.content ?: error("template $key: sections must be strings") }
        w.line("TemplateSpec(")
        w.indent {
            line("key = ${Naming.kotlinString(key)},")
            line("name = Res.string.${Naming.stringResourceName(t.string("nameKey") ?: error("template $key has no nameKey"))},")
            line("description = Res.string.${Naming.stringResourceName(t.string("descriptionKey") ?: error("template $key has no descriptionKey"))},")
            line("sections = listOf(${sections.joinToString(", ") { Naming.kotlinString(it) }}),")
            line("defaultAccent = ${swatches.kotlinName}.${Naming.enumConstant(accent)},")
            line("preview = ${Naming.kotlinString(t.string("preview").orEmpty())},")
            line("order = ${t.long("order") ?: 0},")
        }
        w.line("),")
    }
}
