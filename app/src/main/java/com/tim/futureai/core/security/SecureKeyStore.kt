package com.tim.futureai.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的安全键值存储：
 * 每条值用 Keystore 内 AES/GCM 密钥加密，密文存 SharedPreferences。
 * API Key 明文只存在于内存中，不落盘明文、不进日志。
 */
class SecureKeyStore(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("futureai_secure", Context.MODE_PRIVATE)
    }

    /** 保存明文 → 加密后落盘。返回是否成功。 */
    fun putString(key: String, plainText: String): Boolean = try {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("$key.iv", android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            .putString("$key.data", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .apply()
        true
    } catch (e: Exception) {
        false
    }

    /** 读取并解密；不存在或解密失败返回 null。 */
    fun getString(key: String): String? = try {
        val ivB64 = prefs.getString("$key.iv", null) ?: return null
        val dataB64 = prefs.getString("$key.data", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(
                Cipher.DECRYPT_MODE, getOrCreateKey(),
                GCMParameterSpec(128, android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP)),
            )
        }
        String(cipher.doFinal(android.util.Base64.decode(dataB64, android.util.Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    /** 删除指定条目。 */
    fun remove(key: String) {
        prefs.edit().remove("$key.iv").remove("$key.data").apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "futureai_master_key"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        /** 配置项键名：API Key。 */
        const val KEY_API = "api_key"
        /** 配置项键名：Base URL（非敏感，但统一由本类管理）。 */
        const val KEY_BASE_URL = "base_url"
        /** 配置项键名：模型名。 */
        const val KEY_MODEL = "model"
    }
}
