package com.markvanpraet.dnarelationships

import android.app.Application
import android.content.res.Resources
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.ArrayList

data class Groupings(
    val relationship: String,
    val relCode: String,
    val distance: Int,
    val group: String,
    val relationshipFull: String
)