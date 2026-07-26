package io.github.fornewid.journeys.engine

import java.io.File

/**
 * Holds the device from a second JVM, so [DeviceMutexTest] can prove the lock survives a process
 * boundary — the case a Gradle build service cannot cover.
 */
object DeviceLockHolder {
    @JvmStatic
    fun main(args: Array<String>) {
        val marker = File(args[0])
        val holdMillis = args[1].toLong()
        DeviceMutex.withDevice(timeoutSeconds = 30) {
            marker.writeText("held")
            Thread.sleep(holdMillis)
        }
    }
}
