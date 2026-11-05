package com.example.flipperdroid

import com.example.flipperdroid.reports.Finding
import com.example.flipperdroid.reports.PenetrationReport
import com.example.flipperdroid.reports.ReportGenerator
import com.example.flipperdroid.reports.Severity
import com.example.flipperdroid.security.TotpGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class TotpReportTest {

    // Secrets RFC 6238 en Base32 (vérifiés : décodent vers les graines ASCII de la RFC)
    private val seedSha1 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"                       // "12345678901234567890"
    private val seedSha256 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA===="  // 32 octets
    private val seedSha512 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA="  // 64 octets (vérifié en Python)

    // --- TOTP par algorithme (RFC 6238, Appendix B) ---

    @Test
    fun totp_sha256_vectors() {
        assertEquals("46119246", TotpGenerator.generate(seedSha256, 59, 8, 30, "SHA256"))
        assertEquals("68084774", TotpGenerator.generate(seedSha256, 1111111109, 8, 30, "SHA256"))
    }

    @Test
    fun totp_sha512_vectors() {
        assertEquals("90693936", TotpGenerator.generate(seedSha512, 59, 8, 30, "SHA512"))
        assertEquals("25091201", TotpGenerator.generate(seedSha512, 1111111109, 8, 30, "SHA512"))
    }

    @Test
    fun totp_default_algorithm_is_sha1() {
        assertEquals("94287082", TotpGenerator.generate(seedSha1, 59, 8))
        assertEquals(TotpGenerator.generate(seedSha1, 59, 8), TotpGenerator.generate(seedSha1, 59, 8, 30, "sha-1"))
    }

    // --- HOTP (RFC 4226, Appendix D) ---

    @Test
    fun hotp_rfc4226_vectors() {
        assertEquals("755224", TotpGenerator.hotp(seedSha1, 0))
        assertEquals("287082", TotpGenerator.hotp(seedSha1, 1))
        assertEquals("359152", TotpGenerator.hotp(seedSha1, 2))
    }

    // --- Parsing otpauth:// ---

    @Test
    fun otpauth_parses_algorithm_digits_period_and_decodes_label() {
        val data = TotpGenerator.parseOtpauthUri(
            "otpauth://totp/ACME%20Co%3Ajohn.doe%40example.com" +
                "?secret=JBSWY3DPEHPK3PXP&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60"
        )!!
        assertEquals("john.doe@example.com", data.label)
        assertEquals("JBSWY3DPEHPK3PXP", data.secret)
        assertEquals("ACME Co", data.issuer)
        assertEquals("SHA256", data.algorithm)
        assertEquals(8, data.digits)
        assertEquals(60, data.period)
        assertEquals("totp", data.type)
    }

    @Test
    fun otpauth_defaults_and_algorithm_normalization() {
        val data = TotpGenerator.parseOtpauthUri("otpauth://totp/Plain?secret=JBSWY3DPEHPK3PXP&algorithm=sha-512")!!
        assertEquals("Plain", data.label)
        assertNull(data.issuer)
        assertEquals("SHA512", data.algorithm)
        assertEquals(6, data.digits)
        assertEquals(30, data.period)
    }

    @Test
    fun otpauth_hotp_type_is_reported() {
        val data = TotpGenerator.parseOtpauthUri("otpauth://hotp/svc:bob?secret=JBSWY3DPEHPK3PXP&counter=3")!!
        assertEquals("hotp", data.type)
        assertEquals("bob", data.label)
    }

    @Test
    fun otpauth_rejects_malformed_uris() {
        assertNull(TotpGenerator.parseOtpauthUri("https://example.com/?secret=ABC"))
        assertNull(TotpGenerator.parseOtpauthUri("otpauth://totp/NoSecret?issuer=X"))
        assertNull(TotpGenerator.parseOtpauthUri("otpauth://totp/Bad?secret=JBSWY3DPEHPK3PXP&digits=abc"))
        assertNull(TotpGenerator.parseOtpauthUri("otpauth://other/Bad?secret=JBSWY3DPEHPK3PXP"))
    }

    // --- Base32 ---

    @Test
    fun isValidBase32_accepts_valid_secrets() {
        assertTrue(TotpGenerator.isValidBase32("JBSWY3DPEHPK3PXP"))
        assertTrue(TotpGenerator.isValidBase32("jbsw y3dp ehpk 3pxp"))
        assertTrue(TotpGenerator.isValidBase32("JBSWY3DPEE======"))
        assertTrue(TotpGenerator.isValidBase32("ABC====="))
    }

    @Test
    fun isValidBase32_rejects_invalid_secrets() {
        assertFalse(TotpGenerator.isValidBase32(""))
        assertFalse(TotpGenerator.isValidBase32("   "))
        assertFalse(TotpGenerator.isValidBase32("====="))
        assertFalse(TotpGenerator.isValidBase32("JBSWY3DPEHPK3PX1")) // '1' hors alphabet
        assertFalse(TotpGenerator.isValidBase32("JBSW=Y3DP"))        // '=' au milieu
        assertFalse(TotpGenerator.isValidBase32("JBSWY3DPEHPK3PXP!"))
    }

    // --- Markdown : échappement et fences ---

    @Test
    fun markdown_escapes_special_characters() {
        assertEquals("a\\*b\\_c\\[d\\]", ReportGenerator.escapeMarkdown("a*b_c[d]"))
        assertEquals("\\# \\`x\\`", ReportGenerator.escapeMarkdown("# `x`"))
    }

    @Test
    fun markdown_single_line_flattens_newlines() {
        assertEquals("x y", ReportGenerator.escapeMarkdown("x\ny", singleLine = true))
        assertEquals("x\ny", ReportGenerator.escapeMarkdown("x\ny"))
    }

    @Test
    fun markdown_fence_is_longer_than_any_backtick_run() {
        assertEquals("```", ReportGenerator.markdownFence("plain"))
        assertEquals("````", ReportGenerator.markdownFence("a```b"))
        assertEquals("`````", ReportGenerator.markdownFence("x`` y ````z"))
        assertEquals("```", ReportGenerator.markdownFence("a `` b"))
    }

    @Test
    fun markdown_report_fences_evidence_containing_backticks() {
        val report = sampleReport(
            findings = listOf(finding(id = "FINDING-1", evidence = "echo ```hi```"))
        )
        val md = ReportGenerator.generateMarkdownReport(report)
        assertTrue(md.contains("````\necho ```hi```\n````"))
    }

    @Test
    fun markdown_report_escapes_title_and_finding_title() {
        val report = sampleReport(
            title = "Audit *final*",
            findings = listOf(finding(id = "FINDING-1", title = "XSS <script>"))
        )
        val md = ReportGenerator.generateMarkdownReport(report)
        assertTrue(md.contains("**Project:** Audit \\*final\\*"))
        assertTrue(md.contains("### [HIGH] XSS \\<script\\>"))
    }

    // --- JSON / HTML ---

    @Test
    fun json_cvss_nan_and_infinity_become_zero() {
        val report = sampleReport(
            findings = listOf(
                finding(id = "FINDING-1", cvss = Float.NaN),
                finding(id = "FINDING-2", cvss = Float.POSITIVE_INFINITY)
            )
        )
        val json = ReportGenerator.generateJsonReport(report)
        assertFalse(json.contains("NaN"))
        assertFalse(json.contains("Infinity"))
        assertEquals(2, Regex("\"cvss\": 0\\.0").findAll(json).count())
    }

    @Test
    fun json_and_html_include_escaped_timeline() {
        val report = sampleReport(timeline = listOf("10:00:00 <scan> started", "10:05:00 done"))
        val json = ReportGenerator.generateJsonReport(report)
        assertTrue(json.contains("\"timeline\": ["))
        assertTrue(json.contains("\"10:00:00 <scan> started\""))

        val html = ReportGenerator.generateHtmlReport(report)
        assertTrue(html.contains("Test Timeline"))
        assertTrue(html.contains("<li>10:00:00 &lt;scan&gt; started</li>"))
    }

    // --- Identifiants de findings ---

    @Test
    fun finding_ids_never_reuse_numbers_after_deletion() {
        // 1 et 2 émis, puis 2 supprimé : le prochain doit être 3, pas 2
        val afterDelete = ReportGenerator.nextFindingNumber(listOf("FINDING-1"), lastIssued = 2)
        assertEquals(3, afterDelete)
    }

    @Test
    fun finding_ids_follow_max_existing_suffix() {
        assertEquals(1, ReportGenerator.nextFindingNumber(emptyList(), 0))
        assertEquals(6, ReportGenerator.nextFindingNumber(listOf("FINDING-5"), 0))
        assertEquals(4, ReportGenerator.nextFindingNumber(listOf("FINDING-1", "OTHER", "FINDING-x"), 3))
        assertNull(ReportGenerator.findingNumber("FINDING-x"))
        assertEquals(7, ReportGenerator.findingNumber("FINDING-7"))
    }

    // --- Helpers ---

    private fun finding(
        id: String,
        title: String = "Finding",
        evidence: String = "proof",
        cvss: Float = 0f
    ) = Finding(
        id = id,
        title = title,
        severity = Severity.HIGH,
        description = "desc",
        impact = "impact",
        remediation = "fix",
        evidence = evidence,
        cvss = cvss
    )

    private fun sampleReport(
        title: String = "Report",
        findings: List<Finding> = emptyList(),
        timeline: List<String> = emptyList()
    ) = PenetrationReport(
        title = title,
        date = Date(0L),
        tester = "tester",
        target = "target",
        findings = findings,
        recommendations = emptyList(),
        timeline = timeline
    )
}
