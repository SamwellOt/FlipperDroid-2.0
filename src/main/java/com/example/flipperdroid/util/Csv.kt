package com.example.flipperdroid.util

/** Champ CSV entre guillemets : guillemets doublés, CR/LF supprimés (RFC 4180). */
fun csvField(s: String): String =
    "\"" + s.replace("\r", "").replace("\n", "").replace("\"", "\"\"") + "\""
