package com.chestny.den

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object SecureStorage {
    private const val PREFS_NAME = "chestny_den_secure"
    private const val KEY_ALIAS = "chestny_den_master_key"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getString(key: String, default: String = ""): String {
        return prefs?.getString(key, default) ?: default
    }

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return prefs?.getBoolean(key, default) ?: default
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun getInt(key: String, default: Int = 0): Int {
        return prefs?.getInt(key, default) ?: default
    }

    fun putInt(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
