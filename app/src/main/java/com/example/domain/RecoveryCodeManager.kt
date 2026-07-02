package com.example.domain

import android.util.Base64
import com.example.data.local.entity.RecoveryCodeEntity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Generates and verifies recovery codes fully OFFLINE (no network — works in airplane mode).
 *
 * Codes are shown to the user exactly once at generation. We store only a per-code salt + PBKDF2
 * hash, never the plaintext. Verification re-hashes the typed code and compares in constant time.
 *
 * Codes look like `XXXX-XXXX` using an unambiguous alphabet (no 0/O/1/I/L) so they are easy to
 * write down and read back.
 */
class RecoveryCodeManager {

    data class GeneratedCode(val plaintext: String, val entity: RecoveryCodeEntity)

    /** Generate [count] fresh codes. Returns plaintext (to show once) paired with the row to store. */
    fun generate(count: Int = 10): List<GeneratedCode> {
        val random = SecureRandom()
        val now = System.currentTimeMillis()
        return (0 until count).map {
            val plaintext = randomCode(random)
            val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
            val hash = pbkdf2(canonical(plaintext), salt)
            GeneratedCode(
                plaintext = plaintext,
                entity = RecoveryCodeEntity(
                    codeHash = Base64.encodeToString(hash, Base64.NO_WRAP),
                    salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                    maskedDisplay = MASKED,
                    used = false,
                    usedAt = null,
                    createdAt = now
                )
            )
        }
    }

    /** True if [input] matches this stored code. Accepts any spacing/case/dashes. */
    fun verify(input: String, entity: RecoveryCodeEntity): Boolean {
        val salt = runCatching { Base64.decode(entity.salt, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(entity.codeHash, Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = pbkdf2(canonical(input), salt)
        // MessageDigest.isEqual is constant-time, avoiding timing side-channels.
        return MessageDigest.isEqual(expected, actual)
    }

    /** Strip spaces/dashes, uppercase — so "sl2a 9x7b" and "SL2A-9X7B" hash identically. */
    private fun canonical(input: String): String =
        input.filter { it.isLetterOrDigit() }.uppercase()

    private fun randomCode(random: SecureRandom): String {
        val chars = CharArray(CODE_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        val raw = String(chars)
        return raw.substring(0, 4) + "-" + raw.substring(4, 8)
    }

    private fun pbkdf2(canonical: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(canonical.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        // Unambiguous alphabet: no 0/O/1/I/L to avoid transcription errors.
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 8
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val MASKED = "••••-••••"
    }
}
