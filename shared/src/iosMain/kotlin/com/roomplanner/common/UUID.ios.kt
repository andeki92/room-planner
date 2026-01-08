package com.roomplanner.common

import platform.Foundation.NSUUID

actual fun generateUUID(): String = NSUUID().UUIDString()
