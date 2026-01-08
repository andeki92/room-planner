package com.roomplanner.common

actual fun generateUUID(): String = java.util.UUID.randomUUID().toString()
