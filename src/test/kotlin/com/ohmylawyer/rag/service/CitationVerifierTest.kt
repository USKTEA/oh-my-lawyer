package com.ohmylawyer.rag.service

import kotlin.test.Test
import kotlin.test.assertEquals

class CitationVerifierTest {
    @Test
    fun `extractSearchTerms - law name with article number`() {
        val (term1, term2) = CitationVerifier.extractSearchTerms("개인정보보호법 제18조 제2항")

        assertEquals("개인정보보호법", term1)
        assertEquals("개인정보보호법", term2)
    }

    @Test
    fun `extractSearchTerms - law name with spaces`() {
        val (term1, term2) = CitationVerifier.extractSearchTerms("개인정보 보호법 제18조")

        assertEquals("개인정보 보호법", term1)
        assertEquals("개인정보보호법", term2)
    }

    @Test
    fun `extractSearchTerms - case number`() {
        val (term1, _) = CitationVerifier.extractSearchTerms("2012다105482")

        assertEquals("2012다105482", term1)
    }

    @Test
    fun `extractSearchTerms - case number embedded in text`() {
        val (term1, _) = CitationVerifier.extractSearchTerms("대법원 2012다105482 판결")

        assertEquals("2012다105482", term1)
    }

    @Test
    fun `extractSearchTerms - law name only`() {
        val (term1, term2) = CitationVerifier.extractSearchTerms("공동주택관리법")

        assertEquals("공동주택관리법", term1)
        assertEquals("공동주택관리법", term2)
    }

    @Test
    fun `extractSearchTerms - blank returns empty`() {
        val (term1, term2) = CitationVerifier.extractSearchTerms("")

        assertEquals("", term1)
        assertEquals("", term2)
    }

    @Test
    fun `extractSearchTerms - constitutional case number`() {
        val (term1, _) = CitationVerifier.extractSearchTerms("2016헌마388")

        assertEquals("2016헌마388", term1)
    }
}
