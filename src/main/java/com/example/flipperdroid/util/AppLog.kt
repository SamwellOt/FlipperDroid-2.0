package com.example.flipperdroid.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal applicatif central. Les modules appellent [log] pour tracer leurs
 * actions ; l'écran Logs affiche le flux. En mémoire (max 500 lignes).
 */
object AppLog {
    private const val MAX = 500
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    fun log(tag: String, message: String) {
        val line = "${fmt.format(Date())} [$tag] $message"
        _entries.value = (_entries.value + line).takeLast(MAX)
        android.util.Log.d("FlipperDroid/$tag", message)
    }

    fun clear() { _entries.value = emptyList() }

    fun dump(): String = _entries.value.joinToString("\n")
}
