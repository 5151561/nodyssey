package io.github.bbs1.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstanceUrlTest {
    @Test
    fun `a bare domain becomes an https origin`() {
        assertEquals("https://bbs1.org", normalizeInstanceUrl("bbs1.org"))
    }

    @Test
    fun `a pasted page url is cut down to its origin`() {
        assertEquals(
            "https://bbs.example.com",
            normalizeInstanceUrl("https://bbs.example.com/thread/42?page=2#post-7"),
        )
    }

    @Test
    fun `explicit http is kept rather than upgraded`() {
        assertEquals("http://192.168.1.10", normalizeInstanceUrl("http://192.168.1.10/"))
    }

    @Test
    fun `a non-default port survives and a default port is dropped`() {
        assertEquals("https://bbs1.org:8443", normalizeInstanceUrl("bbs1.org:8443"))
        assertEquals("https://bbs1.org", normalizeInstanceUrl("https://bbs1.org:443"))
        assertEquals("http://bbs1.org", normalizeInstanceUrl("http://bbs1.org:80"))
    }

    @Test
    fun `host case is folded so one site cannot become two entries`() {
        assertEquals("https://bbs1.org", normalizeInstanceUrl("  HTTPS://BBS1.ORG  "))
    }

    @Test
    fun `blank and unparseable input is rejected`() {
        assertNull(normalizeInstanceUrl(""))
        assertNull(normalizeInstanceUrl("   "))
        assertNull(normalizeInstanceUrl("not a url"))
        assertNull(normalizeInstanceUrl("https://"))
    }

    @Test
    fun `schemes other than http are rejected`() {
        assertNull(normalizeInstanceUrl("ftp://bbs1.org"))
        assertNull(normalizeInstanceUrl("javascript:alert(1)"))
    }

    @Test
    fun `userinfo is rejected because it reads as one site and connects to another`() {
        assertNull(normalizeInstanceUrl("https://evil@bbs1.org"))
    }

    @Test
    fun `the display-name fallback is the host`() {
        assertEquals("bbs1.org", instanceHost("https://bbs1.org"))
        assertEquals("bbs.example.com", instanceHost("https://bbs.example.com:8443"))
    }
}
