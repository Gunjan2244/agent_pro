package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `tokens.css` for the in-app site preview: the same output as `scripts/gen-tokens.mjs` (which writes it
 * into web/dist for the hosted site). The preview's WebView loads `/tokens.css` from the Android assets,
 * where `web/` has no generated file, so `copySitePreviewAssets` writes it with this generator.
 * Keep the two in step: `GeneratorsTest` compares the variable names with the script.
 */
object TokensCssGenerator {
    private val TEXT_KEYS = listOf("s", "m", "l", "xl")
    private val SPACE_KEYS = listOf("s", "m", "l")

    fun generate(designTokens: JsonObject): String {
        val color = designTokens.obj("color").obj("light")
        val text = designTokens.obj("text")
        val space = designTokens.obj("space")
        val shape = designTokens.obj("shape")
        val motion = designTokens.obj("motion")
        val lines = mutableListOf(":root{")
        color.forEach { (k, v) -> lines += "--c-$k:${v.jsonPrimitive.content};" }
        lines += "--accent:${color.getValue("primary").jsonPrimitive.content};"
        TEXT_KEYS.forEach { k ->
            val t = text.obj(k)
            lines += "--text-$k:${t.num("size")}px;--line-$k:${t.num("line")}px;"
        }
        SPACE_KEYS.forEach { k -> lines += "--space-$k:${space.num(k)}px;" }
        shape.keys.forEach { k -> lines += "--shape-$k:${shape.num(k)}px;" }
        motion.keys.forEach { k -> lines += "--motion-$k:${motion.num(k)}ms;" }
        lines += "}"
        val extra = (text["hiLineExtra"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
        lines += ":lang(hi){" + TEXT_KEYS.joinToString("") { k -> "--line-$k:${fmt(text.obj(k).numValue("line") + extra)}px;" } + "}"
        return lines.joinToString("\n") + "\n"
    }

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw SharedDefinitionsException(listOf("design-tokens.json: \"$key\" is missing"))

    private fun JsonObject.numValue(key: String): Double =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
            ?: throw SharedDefinitionsException(listOf("design-tokens.json: \"$key\" must be a number"))

    private fun JsonObject.num(key: String): String = fmt(numValue(key))

    private fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
