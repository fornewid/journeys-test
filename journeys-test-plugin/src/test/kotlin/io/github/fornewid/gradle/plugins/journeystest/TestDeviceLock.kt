package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.DeviceMutex
import java.io.File

/**
 * Keeps a test build's device lock inside its own project directory.
 *
 * Agents in these tests are shell stubs that never touch a device, but they still claim it — that
 * claim is machine-wide by design. Without this the suite would queue behind a developer's real
 * journey run, and hold the device against it for as long as the tests took.
 */
internal fun lockDirArg(projectDir: File): String = "-D${DeviceMutex.LOCK_DIR_PROPERTY}=${File(projectDir, "device-locks").path}"
