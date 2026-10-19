package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generates `Templates.kt` (package com.sledje.agentpro.core.catalog) from `shared/templates/index.json`:
 * a `TemplateSpec` per template (key, translated name and description, supported section keys, the
 * default accent as an `AccentSwatch`, preview path, order) and `Templates.all` in display order.
 * When the catalog has `templateFamilies` (DECISIONS #76), each template also carries its `family: TemplateFamily`
 * and `Templates.inFamily(family)` lists a family's templates; the generated API is unchanged otherwise.
 * The onboarding and Website template pickers read it, so template data is never hard-coded in Kotlin.
 */
class TemplatesKotlinGenerator(private val defs: SharedDefinitions) {

    fun generate(): String {
        val swatches = defs.catalogList("accentSwatches")
        val families = defs.catalogList(FAMILIES)
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
            if (families != null) {
                line("/** The design family (catalog.templateFamilies). */")
                line("val family: ${families.kotlinName},")
            }
        }
        w.line(")")
        w.line()
        w.line("/** `shared/templates/index.json`: the website templates, in display order. */")
        w.line("object Templates {")
        w.indent {
            line("const val VERSION: Int = ${defs.templates.long("version") ?: 0}")
            line()
            line("val all: List<TemplateSpec> = listOf(")
            indent { templates.forEach { writeTemplate(this, it, swatches, families) } }
            line(")")
            line()
            line("fun fromKey(key: String): TemplateSpec? = all.firstOrNull { it.key == key }")
            if (families != null) {
                line()
                line("/** The templates of [family], in display order. */")
                line("fun inFamily(family: ${families.kotlinName}): List<TemplateSpec> = all.filter { it.family == family }")
            }
        }
        w.line("}")
        return w.toString()
    }

    private fun writeTemplate(w: CodeWriter, t: JsonObject, swatches: CatalogList?, families: CatalogList?) {
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
            if (families != null) {
                val family = t.string("family")
                require(family != null && family in families.keys) { "template $key: family \"$family\" is not a templateFamilies key" }
                line("family = ${families.kotlinName}.${Naming.enumConstant(family)},")
            }
        }
        w.line("),")
    }

    companion object {
        /** Catalog list labelling the template families (DECISIONS #76). */
        const val FAMILIES = "templateFamilies"
    }
}
