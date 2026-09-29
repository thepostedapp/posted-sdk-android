package com.postedapp.posted

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the README "### Posted" API reference section against silently drifting from Posted.kt's
 * actual public surface (POS-183 hand-aligned them once; nothing enforced it afterward). Cheap
 * regex over source text — no Kotlin reflection: fails if Posted.kt gains/renames a public method
 * that isn't mentioned anywhere in that README section, or if the README's fenced `kotlin` block
 * still lists a method Posted.kt no longer declares.
 *
 * Scoped to posted-sdk (Android) docs only; cross-platform README parity is tracked by POS-256.
 */
class PostedApiDocDriftGuardTest {
    @Test
    fun `every public Posted method is documented in the README`() {
        val undocumented = actualPublicMethodNames() - readmeMentionedMethodNames()
        assertTrue(
            "Posted.kt public method(s) $undocumented are not mentioned in README.md's " +
                "'### Posted' API reference section — update README.md to match.",
            undocumented.isEmpty(),
        )
    }

    @Test
    fun `README fenced API block does not list removed Posted methods`() {
        val stale = readmeFencedMethodNames() - actualPublicMethodNames()
        assertTrue(
            "README.md's '### Posted' fenced API block lists method(s) $stale that no longer " +
                "exist on Posted — update README.md to match Posted.kt.",
            stale.isEmpty(),
        )
    }

    // Unit tests run with the module directory (posted-sdk/) as the working directory, so the
    // repo root — where README.md lives — is one level up.
    private fun postedKtSource() = File("src/main/java/com/postedapp/posted/Posted.kt").readText()

    private fun readmeSource() = File("../README.md").readText()

    private val funDeclaration = Regex("""(?m)^\s*(?:suspend\s+)?fun\s+(\w+)\(""")

    // object Posted is the only top-level declaration below this point in the file, so everything
    // after the opening brace is its body — no brace-matching needed. A line-based "fun" match
    // (with no leading "private ") is enough to isolate public methods, since every private
    // helper in this file declares "private" on the same source line as "fun".
    private fun actualPublicMethodNames(): Set<String> {
        val objectBody = postedKtSource().substringAfter("object Posted {")
        return funDeclaration.findAll(objectBody).map { it.groupValues[1] }.toSet()
    }

    private fun postedReadmeSection(): String =
        readmeSource()
            .substringAfter("### Posted\n")
            .substringBefore("\n### PostedDelegate")

    private fun readmeMentionedMethodNames(): Set<String> =
        Regex("""\b(\w+)\(""").findAll(postedReadmeSection()).map { it.groupValues[1] }.toSet()

    private fun readmeFencedMethodNames(): Set<String> {
        val fenced = postedReadmeSection().substringAfter("```kotlin").substringBefore("```")
        return funDeclaration.findAll(fenced).map { it.groupValues[1] }.toSet()
    }
}
