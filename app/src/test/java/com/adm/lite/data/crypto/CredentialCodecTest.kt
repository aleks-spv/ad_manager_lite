/*
 * Copyright 2026 Alexandr <aleks.spv@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adm.lite.data.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
class CredentialCodecTest {

    // Один ключ на тест: seal и open вызывают key() каждый раз, новый ключ на каждый вызов не расшифруется
    private val testKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val fakeKeyProvider = SecretKeyProvider { testKey }

    private val codec = CredentialCodec(fakeKeyProvider)

    @Test
    fun sealAndOpenAscii() {
        val original = "password123"
        val sealed = codec.seal(original)
        assertEquals(original, codec.open(sealed))
    }

    @Test
    fun sealAndOpenCyrillic() {
        val original = "пароль123"
        val sealed = codec.seal(original)
        assertEquals(original, codec.open(sealed))
    }

    @Test
    fun sealAndOpenEmoji() {
        val original = "password🔑"
        val sealed = codec.seal(original)
        assertEquals(original, codec.open(sealed))
    }

    @Test
    fun twoSealsProduceDifferentBlobs() {
        val original = "same_password"
        val blob1 = codec.seal(original)
        val blob2 = codec.seal(original)
        assertNotEquals(blob1, blob2)
    }

    @Test
    fun corruptedBlobThrows() {
        val blob = codec.seal("test")
        val corrupted = blob.toCharArray().also { it[5] = if (it[5] == 'A') 'B' else 'A' }.concatToString()
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            codec.open(corrupted)
        }
    }

    @Test
    fun tooShortBlobThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            codec.open("dGVzdA==") // "test" in base64 = 4 bytes < 13
        }
    }
}
