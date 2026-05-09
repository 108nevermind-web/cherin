package com.cherin.edupsych.data

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * On-device English -> Korean translation. First call downloads ~30MB model
 * via Play Services; subsequent calls are fully offline.
 */
object EnKoTranslator {

    private val client: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.KOREAN)
                .build()
        )
    }

    private var modelReady = false

    suspend fun translate(text: String): String {
        if (!modelReady) {
            client.downloadModelIfNeeded().await()
            modelReady = true
        }
        return client.translate(text).await()
    }
}
