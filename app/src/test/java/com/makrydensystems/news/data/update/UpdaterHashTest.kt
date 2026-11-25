package com.makrydensystems.news.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-23 (2026-11-25): the updater verifies the downloaded APK against
 * the manifest's published SHA-256 and fails closed on a mismatch.
 * Known vectors pin the hex encoding (a sign-extension slip here
 * would pass a wrong hash or fail a right one).
 */
class UpdaterHashTest {

    @Test
    fun `sha256 of abc matches the known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun `sha256 of empty input matches the known vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `empty expected hash means nothing published so nothing to check`() {
        assertTrue(sha256Matches("abc".toByteArray(), ""))
        assertTrue(sha256Matches("abc".toByteArray(), "   "))
    }

    @Test
    fun `matching hash passes case-insensitively`() {
        val hash = sha256Hex("abc".toByteArray())
        assertTrue(sha256Matches("abc".toByteArray(), hash))
        assertTrue(sha256Matches("abc".toByteArray(), hash.uppercase()))
    }

    @Test
    fun `wrong hash and malformed hash fail closed`() {
        assertFalse(sha256Matches("abc".toByteArray(), "0".repeat(64)))
        assertFalse(sha256Matches("abc".toByteArray(), "not-a-hash"))
        assertFalse(sha256Matches("abc".toByteArray(), "ba7816bf"))
    }

    @Test
    fun `file check verifies streamed bytes against the published hash`() {
        val file = File.createTempFile("apk-check", ".bin")
        try {
            file.writeBytes("abc".toByteArray())
            val hash = sha256Hex("abc".toByteArray())
            assertTrue(verifyApkSha256(file, hash))
            assertFalse(verifyApkSha256(file, "0".repeat(64)))
            assertTrue(verifyApkSha256(file, ""))
        } finally {
            file.delete()
        }
    }
}
