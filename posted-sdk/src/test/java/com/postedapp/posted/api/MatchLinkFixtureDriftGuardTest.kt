package com.postedapp.posted.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the hand-authored schema-match-link-deterministic.json /
 * schema-match-link-probabilistic.json fixtures (consumed by [ContractConformanceTest]) against
 * silently drifting from the canonical detour-backend-contract.yaml match-link `oneOf` branches
 * (DeterministicMatch | ProbabilisticFingerprint). If the yaml's properties/required list for
 * either branch changes without the corresponding fixture being updated, this test fails —
 * conformance can no longer pass against a stale local schema.
 *
 * There is no other yaml consumer in this module, so this extracts only the two known schema
 * blocks by their fixed indentation shape rather than pulling in a general-purpose YAML library.
 */
class MatchLinkFixtureDriftGuardTest {
    @Test
    fun `deterministic fixture matches canonical DeterministicMatch branch`() {
        assertFixtureMatchesContract(
            yamlSchemaName = "DeterministicMatch",
            fixtureFileName = "schema-match-link-deterministic",
        )
    }

    @Test
    fun `probabilistic fixture matches canonical ProbabilisticFingerprint branch`() {
        assertFixtureMatchesContract(
            yamlSchemaName = "ProbabilisticFingerprint",
            fixtureFileName = "schema-match-link-probabilistic",
        )
    }

    private fun assertFixtureMatchesContract(
        yamlSchemaName: String,
        fixtureFileName: String,
    ) {
        val contractBranch = extractYamlSchemaBranch(yamlSchemaName)
        val fixture = loadFixtureSchema(fixtureFileName)

        assertEquals(
            "'$fixtureFileName' properties drifted from contract yaml's $yamlSchemaName branch",
            contractBranch.propertyNames,
            fixture.propertyNames,
        )
        assertEquals(
            "'$fixtureFileName' required fields drifted from contract yaml's $yamlSchemaName branch",
            contractBranch.required,
            fixture.required,
        )
        assertTrue(
            "'$fixtureFileName' must mirror the contract's additionalProperties: false",
            fixture.additionalPropertiesFalse,
        )
    }

    private data class SchemaShape(
        val propertyNames: Set<String>,
        val required: Set<String>,
        val additionalPropertiesFalse: Boolean,
    )

    private fun loadFixtureSchema(name: String): SchemaShape {
        val text =
            javaClass.classLoader!!.getResourceAsStream("fixtures/$name.json")
                ?.bufferedReader()?.readText()
                ?: error("Missing test fixture: fixtures/$name.json")
        val json = JsonParser.parseString(text).asJsonObject
        val propertyNames = json.getAsJsonObject("properties").keySet()
        val required = json.getAsJsonArray("required").map { it.asString }.toSet()
        val additionalPropertiesFalse = json.get("additionalProperties")?.asBoolean == false
        return SchemaShape(propertyNames, required, additionalPropertiesFalse)
    }

    private fun extractYamlSchemaBranch(schemaName: String): SchemaShape {
        val yamlLines =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/detour-backend-contract.yaml")
                ?.bufferedReader()?.readLines()
                ?: error("Missing test fixture: fixtures/detour-backend-contract.yaml")

        val startIdx = yamlLines.indexOfFirst { it == "    $schemaName:" }
        check(startIdx >= 0) { "Schema '$schemaName' not found in detour-backend-contract.yaml" }

        val relativeBlockEnd =
            yamlLines.drop(startIdx + 1).indexOfFirst { line ->
                line.isNotBlank() && !line.startsWith("      ")
            }
        val blockEnd = if (relativeBlockEnd == -1) yamlLines.size else startIdx + 1 + relativeBlockEnd
        val block = yamlLines.subList(startIdx, blockEnd)

        val propertiesStart = block.indexOfFirst { it == "      properties:" }
        check(propertiesStart >= 0) { "'$schemaName' has no properties: block" }
        val propertyNames =
            block
                .drop(propertiesStart + 1)
                .takeWhile { it.startsWith("        ") }
                .mapNotNull { line -> Regex("""^ {8}(\w+):""").find(line)?.groupValues?.get(1) }
                .toSet()
        check(propertyNames.isNotEmpty()) {
            "'$schemaName' properties extraction found nothing — parser likely drifted from yaml formatting"
        }

        val requiredLine =
            block.firstOrNull { it.trim().startsWith("required:") }
                ?: error("'$schemaName' has no required: line")
        val required =
            Regex("""required:\s*\[(.*)]""").find(requiredLine)
                ?.groupValues?.get(1)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: error("Could not parse required: list from '$requiredLine'")

        val additionalPropertiesFalse = block.any { it.trim() == "additionalProperties: false" }

        return SchemaShape(propertyNames, required, additionalPropertiesFalse)
    }
}
