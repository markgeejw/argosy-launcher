package com.nendo.argosy.data.model.stream

data class StreamRequest(
    val host: String,
    val appId: String,
    val appName: String,
    val hdr: Boolean = true
)
