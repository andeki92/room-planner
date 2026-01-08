package com.roomplanner.common

/**
 * Platform-specific UUID generation.
 * Use expect/actual pattern for iOS/Android.
 */
expect fun generateUUID(): String
