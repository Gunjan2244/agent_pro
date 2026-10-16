package com.sledje.agentpro.buildlogic.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generates `Tokens.kt` (package com.sledje.agentpro.core.designsystem) from design-tokens.json.
 * Colours become `Color`, spacing and shapes `Dp`, type sizes `TextUnit` (sp). The web-only text size
 * (`webOnly: true`, i.e. `text.xl`) is deliberately not generated, so the app can't use it.
 */
class TokensKotlinGenerator(private val defs: SharedDefinitions) {

    fun generate(): String {
        val t = defs.designTokens
        val w = CodeWriter()
        w.raw(Naming.GENERATED_HEADER)
        w.line("@file:Suppress(\"unused\", \"MemberVisibilityCanBePrivate\")")
        w.line()
        w.line("package ${Naming.DESIGN_PACKAGE}")
        w.line()
        w.line("import androidx.compose.ui.graphics.Color")
        w.line("import androidx.compose.ui.unit.Dp")
        w.line("import androidx.compose.ui.unit.TextUnit")
        w.line("import androidx.compose.ui.unit.dp")
        w.line("import androidx.compose.ui.unit.sp")
        w.line("import ${Naming.CATALOG_PACKAGE}.Tone")
        w.line()
        w.line("data class ToneColors(val text: Color, val container: Color)")
        w.line("data class ToneSet(val light: ToneColors, val dark: ToneColors)")
        w.line("data class TextToken(val size: TextUnit, val lineHeight: TextUnit)")
        w.line()
        w.line("object Tokens {")
        w.indent {
            line("const val VERSION: Int = ${t.long("version") ?: 0}")
            for ((name, value) in t) {
                if (name == "version") continue
                val obj = value as? JsonObject ?: continue
                line()
                when (name) {
                    "color" -> obj.forEach { (scheme, colors) ->
                        line("/** `color.$scheme` */")
                        line("object Color${Naming.objectName(scheme)} {")
                        indent {
                            (colors as JsonObject).forEach { (role, hex) ->
                                line("val ${Naming.propertyName(role)}: Color = ${color(hex.primitiveString()!!)}")
                            }
                        }
                        line("}")
                    }
                    "tone" -> writeTones(this, obj)
                    "chart" -> writeChart(this, obj)
                    "text" -> writeText(this, obj)
                    "space", "shape" -> {
                        line("object ${Naming.objectName(name)} {")
                        indent { obj.forEach { (k, v) -> line("val ${Naming.propertyName(k)}: Dp = ${(v as JsonPrimitive).content}.dp") } }
                        line("}")
                    }
                    "motion" -> {
                        line("/** Durations in milliseconds. */")
                        CatalogKotlinGenerator.writeObject(this, "Motion", obj)
                    }
                    else -> CatalogKotlinGenerator.writeObject(this, Naming.objectName(name), obj)
                }
            }
        }
        w.line("}")
        return w.toString()
    }

    private fun writeTones(w: CodeWriter, tones: JsonObject) {
        fun colors(o: JsonObject?) =
            "ToneColors(text = ${color(o?.string("text")!!)}, container = ${color(o.string("container")!!)})"
        w.line("object Tones {")
        w.indent {
            tones.forEach { (name, v) ->
                val o = v as JsonObject
                line("val ${Naming.propertyName(name)}: ToneSet = ToneSet(")
                indent {
                    line("light = ${colors(o.obj("light"))},")
                    line("dark = ${colors(o.obj("dark"))},")
                }
                line(")")
            }
            line()
            line("fun of(tone: Tone): ToneSet = when (tone) {")
            indent { tones.keys.forEach { line("Tone.${Naming.enumConstant(it)} -> ${Naming.propertyName(it)}") } }
            line("}")
        }
        w.line("}")
    }

    private fun writeChart(w: CodeWriter, chart: JsonObject) {
        w.line("object Chart {")
        w.indent {
            chart.forEach { (name, v) ->
                (v as JsonObject).forEach { (scheme, list) ->
                    val colors = (list as JsonArray).joinToString(", ") { color(it.primitiveString()!!) }
                    line("val ${Naming.propertyName(name)}${Naming.objectName(scheme)}: List<Color> = listOf($colors)")
                }
            }
        }
        w.line("}")
    }

    private fun writeText(w: CodeWriter, text: JsonObject) {
        w.line("object Text {")
        w.indent {
            text.forEach { (name, v) ->
                when {
                    v is JsonObject && v.bool("webOnly") == true ->
                        line("// text.$name (${v.long("size")}sp) is web-only and not available in the app.")
                    v is JsonObject ->
                        line("val ${Naming.propertyName(name)}: TextToken = TextToken(${v.long("size")}.sp, ${v.long("line")}.sp)")
                    name == "hiLineExtra" -> {
                        line("/** Added to every line height in the `hi` locale. */")
                        line("val hiLineExtra: TextUnit = ${(v as JsonPrimitive).content}.sp")
                    }
                    v is JsonPrimitive -> {
                        val isInt = v.isIntegral()
                        line("const val ${Naming.propertyName(name)}: ${if (isInt) "Int" else "Double"} = ${if (isInt) v.content else v.content.toDouble().toString()}")
                    }
                }
            }
        }
        w.line("}")
    }

    companion object {
        /** "#RRGGBB" or "#AARRGGBB" -> `Color(0xAARRGGBB)`. */
        fun color(hex: String): String {
            val h = hex.removePrefix("#").uppercase()
            val argb = when (h.length) {
                6 -> "FF$h"
                8 -> h
                else -> throw IllegalArgumentException("Invalid colour \"$hex\"")
            }
            require(argb.all { it in '0'..'9' || it in 'A'..'F' }) { "Invalid colour \"$hex\"" }
            return "Color(0x$argb)"
        }
    }
}
