package com.dilipkumarkv.localgguf.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper for GBNF (GGML BNF Grammar) specification, JSON Schema to GBNF translation,
 * and grammar validation for constrained llama.cpp decoding.
 */
object GbnfGrammarHelper {

    enum class GrammarType(val displayName: String, val description: String) {
        NONE("Standard Text", "Freeform natural language generation"),
        JSON_OBJECT("JSON Object", "Guarantees output is a syntactically valid JSON object"),
        JSON_ARRAY("JSON Array", "Guarantees output is a syntactically valid JSON array"),
        JSON_SCHEMA("JSON Schema", "Forces output to conform strictly to a custom JSON Schema"),
        CHOICE_ENUM("Enum / Choices", "Constrains response to one of a list of predefined options"),
        NUMERIC("Number / Integer", "Restricts output to purely numeric digits"),
        BOOLEAN("Boolean", "Restricts output strictly to 'true' or 'false'"),
        KEY_VALUE("Key-Value Pairs", "Outputs formatted YAML-style Key: Value entries"),
        CUSTOM("Custom GBNF", "Write custom GGML BNF grammar rules")
    }

    data class GrammarPreset(
        val id: String,
        val title: String,
        val type: GrammarType,
        val gbnf: String,
        val schemaJson: String? = null,
        val samplePrompt: String
    )

    // Standard RFC-compliant GBNF base definitions for JSON
    const val GBNF_JSON_OBJECT = """
root   ::= object
value  ::= object | array | string | number | ("true" | "false" | "null") ws

object ::=
  "{" ws (
            string ":" ws value
    ("," ws string ":" ws value)*
  )? "}" ws

array  ::=
  "[" ws (
            value
    ("," ws value)*
  )? "]" ws

string ::=
  "\"" (
    [^"\\] |
    "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F])
  )* "\"" ws

number ::= ("-"? ([0-9] | [1-9] [0-9]*)) ("." [0-9]+)? ([eE] [-+]? [0-9]+)? ws

ws ::= ([ \t\n\r])*
"""

    const val GBNF_JSON_ARRAY = """
root   ::= array
value  ::= object | array | string | number | ("true" | "false" | "null") ws

object ::=
  "{" ws (
            string ":" ws value
    ("," ws string ":" ws value)*
  )? "}" ws

array  ::=
  "[" ws (
            value
    ("," ws value)*
  )? "]" ws

string ::=
  "\"" (
    [^"\\] |
    "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F])
  )* "\"" ws

number ::= ("-"? ([0-9] | [1-9] [0-9]*)) ("." [0-9]+)? ([eE] [-+]? [0-9]+)? ws

ws ::= ([ \t\n\r])*
"""

    const val GBNF_NUMERIC = """
root ::= ("-"? [0-9]+ ("." [0-9]+)?)
"""

    const val GBNF_BOOLEAN = """
root ::= ("true" | "false")
"""

    const val GBNF_KEY_VALUE = """
root ::= (entry "\n")* entry?
entry ::= [a-zA-Z0-9_-]+ ":" [ \t]+ [^\n]+
"""

    val BUILT_IN_PRESETS = listOf(
        GrammarPreset(
            id = "sentiment",
            title = "Sentiment Classifier",
            type = GrammarType.CHOICE_ENUM,
            gbnf = createChoiceEnumGbnf(listOf("POSITIVE", "NEGATIVE", "NEUTRAL")),
            samplePrompt = "Analyze the sentiment of this review: 'The app runs remarkably fast on my phone!'"
        ),
        GrammarPreset(
            id = "entity_extractor",
            title = "Entity Extraction (JSON)",
            type = GrammarType.JSON_SCHEMA,
            schemaJson = """{
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "age": { "type": "integer" },
    "role": { "type": "string" },
    "skills": { "type": "array" }
  },
  "required": ["name", "role"]
}""",
            gbnf = convertJsonSchemaToGbnf("""{
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "age": { "type": "integer" },
    "role": { "type": "string" },
    "skills": { "type": "array" }
  },
  "required": ["name", "role"]
}"""),
            samplePrompt = "Extract candidate details from: 'Alex Rivers is a 29-year-old Senior Android Engineer skilled in Kotlin, C++, and Jetpack Compose.'"
        ),
        GrammarPreset(
            id = "task_planner",
            title = "Task / Action Planner",
            type = GrammarType.JSON_SCHEMA,
            schemaJson = """{
  "type": "object",
  "properties": {
    "task": { "type": "string" },
    "priority": { "type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"] },
    "estimated_minutes": { "type": "integer" },
    "completed": { "type": "boolean" }
  },
  "required": ["task", "priority", "estimated_minutes"]
}""",
            gbnf = convertJsonSchemaToGbnf("""{
  "type": "object",
  "properties": {
    "task": { "type": "string" },
    "priority": { "type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"] },
    "estimated_minutes": { "type": "integer" },
    "completed": { "type": "boolean" }
  },
  "required": ["task", "priority", "estimated_minutes"]
}"""),
            samplePrompt = "Create a structured plan for optimizing memory footprint in a mobile app."
        ),
        GrammarPreset(
            id = "yes_no",
            title = "Boolean / Decision",
            type = GrammarType.BOOLEAN,
            gbnf = GBNF_BOOLEAN,
            samplePrompt = "Is offline on-device inference more private than cloud API calls?"
        ),
        GrammarPreset(
            id = "json_generic",
            title = "General JSON Object",
            type = GrammarType.JSON_OBJECT,
            gbnf = GBNF_JSON_OBJECT,
            samplePrompt = "Generate a JSON configuration for a local machine learning benchmark."
        )
    )

    /**
     * Builds a GBNF grammar that forces the output to be one of the specified choice strings.
     */
    fun createChoiceEnumGbnf(choices: List<String>): String {
        if (choices.isEmpty()) return "root ::= [^\n]+"
        val formattedChoices = choices.map { choice ->
            val escaped = choice.replace("\\", "\\\\").replace("\"", "\\\"")
            "\"$escaped\""
        }.joinToString(" | ")
        return """
root ::= ($formattedChoices)
ws   ::= [ \t\n\r]*
""".trimIndent()
    }

    /**
     * Converts a basic or intermediate JSON Schema into a GBNF grammar specification.
     */
    fun convertJsonSchemaToGbnf(schemaJson: String): String {
        return try {
            val rootObj = JSONObject(schemaJson.trim())
            val properties = rootObj.optJSONObject("properties") ?: JSONObject()
            val requiredArray = rootObj.optJSONArray("required") ?: JSONArray()
            val requiredSet = mutableSetOf<String>()
            for (i in 0 until requiredArray.length()) {
                requiredSet.add(requiredArray.getString(i))
            }

            val sb = StringBuilder()
            sb.append("root ::= \"{\" ws ")

            val keys = properties.keys().asSequence().toList()
            if (keys.isEmpty()) {
                return GBNF_JSON_OBJECT.trimIndent()
            }

            // Build property rules
            val propertyRules = mutableListOf<String>()
            keys.forEachIndexed { index, key ->
                val propObj = properties.optJSONObject(key) ?: JSONObject()
                val propType = propObj.optString("type", "string")
                val isRequired = requiredSet.contains(key) || requiredSet.isEmpty()
                val enumArray = propObj.optJSONArray("enum")

                val ruleName = "prop_${key.replace(Regex("[^a-zA-Z0-9_]"), "_")}"
                val ruleDef = when {
                    enumArray != null && enumArray.length() > 0 -> {
                        val choices = (0 until enumArray.length()).map { i ->
                            "\"\\\"" + enumArray.getString(i).replace("\"", "\\\"") + "\\\"\""
                        }.joinToString(" | ")
                        "$ruleName ::= \"\\\"$key\\\":\" ws ($choices)"
                    }
                    propType == "integer" -> {
                        "$ruleName ::= \"\\\"$key\\\":\" ws [0-9]+"
                    }
                    propType == "number" -> {
                        "$ruleName ::= \"\\\"$key\\\":\" ws (\"-\"? [0-9]+ (\".\" [0-9]+)?)"
                    }
                    propType == "boolean" -> {
                        "$ruleName ::= \"\\\"$key\\\":\" ws (\"true\" | \"false\")"
                    }
                    propType == "array" -> {
                        "$ruleName ::= \"\\\"$key\\\":\" ws \"[\" ws (string (\",\" ws string)*)? \"]\""
                    }
                    else -> {
                        "$ruleName ::= \"\\\"$key\\\":\" ws string"
                    }
                }
                propertyRules.add(ruleDef)
            }

            val propListStr = keys.mapIndexed { idx, key ->
                val ruleName = "prop_${key.replace(Regex("[^a-zA-Z0-9_]"), "_")}"
                if (idx == 0) ruleName else "\",\" ws $ruleName"
            }.joinToString(" ")

            sb.append(propListStr)
            sb.append(" \"}\" ws\n\n")

            propertyRules.forEach { rule ->
                sb.append(rule).append("\n")
            }

            sb.append("""
string ::= "\"" ([^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]))* "\"" ws
ws     ::= [ \t\n\r]*
""")
            sb.toString().trimIndent()
        } catch (e: Exception) {
            // Fallback to general JSON object grammar if schema is invalid JSON
            GBNF_JSON_OBJECT.trimIndent()
        }
    }

    /**
     * Validates if a GBNF grammar string is syntactically well-formed.
     */
    fun validateGbnf(gbnf: String): ValidationResult {
        val trimmed = gbnf.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(isValid = true, message = "Empty grammar (no constraints)")
        }

        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.none { it.startsWith("root") && it.contains("::=") }) {
            return ValidationResult(
                isValid = false,
                message = "Missing 'root ::= ...' entry rule. A valid GBNF grammar must declare a root rule."
            )
        }

        // Check for unbalanced quotes, brackets, and parentheses
        var inQuotes = false
        var inCharClass = false
        var escaped = false
        var parens = 0

        for (char in trimmed) {
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }

            if (inQuotes) {
                if (char == '"') {
                    inQuotes = false
                }
            } else if (inCharClass) {
                if (char == ']') {
                    inCharClass = false
                }
            } else {
                when (char) {
                    '"' -> inQuotes = true
                    '[' -> inCharClass = true
                    '(' -> parens++
                    ')' -> {
                        parens--
                        if (parens < 0) {
                            return ValidationResult(isValid = false, message = "Unmatched closing parenthesis ')'")
                        }
                    }
                }
            }
        }

        if (inQuotes) {
            return ValidationResult(isValid = false, message = "Unclosed quote '\"' in grammar string")
        }
        if (inCharClass) {
            return ValidationResult(isValid = false, message = "Unclosed character class '[' in grammar definition")
        }
        if (parens != 0) {
            return ValidationResult(isValid = false, message = "Unclosed parenthesis '(' in grammar definition")
        }

        return ValidationResult(isValid = true, message = "Valid GBNF grammar syntax")
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}
