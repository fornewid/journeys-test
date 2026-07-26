package io.github.fornewid.journeys.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The device has to be held against other builds on the machine, not just other tasks in this one,
 * so every case here puts the competing holder in a separate JVM.
 */
class DeviceMutexTest {
    @TempDir
    lateinit var lockDir: File

    @BeforeEach
    fun useTemporaryLockDir() {
        // Never contend with the developer's own runs while the suite is going.
        System.setProperty(DeviceMutex.LOCK_DIR_PROPERTY, lockDir.path)
    }

    @AfterEach
    fun restoreLockDir() {
        System.clearProperty(DeviceMutex.LOCK_DIR_PROPERTY)
    }

    @Test
    fun `another process holding the device makes this one wait for it`() {
        val holder = holdFromAnotherProcess(holdMillis = 2_000)

        var waitedOn: File? = null
        val startedAt = System.nanoTime()
        val acquiredAfter =
            DeviceMutex.withDevice(timeoutSeconds = 30, onWait = { waitedOn = it }) {
                (System.nanoTime() - startedAt) / 1_000_000
            }

        assertNotNull(waitedOn, "should have reported that it was waiting for the other build")
        assertTrue(
            acquiredAfter >= 1_000,
            "took the device after only ${acquiredAfter}ms, so it did not wait for the other process",
        )
        assertEquals(0, holder.waitFor())
    }

    @Test
    fun `giving up names the lock file, so a stale one can be removed`() {
        val holder = holdFromAnotherProcess(holdMillis = 10_000)

        val failure =
            assertThrows<IllegalStateException> {
                DeviceMutex.withDevice(timeoutSeconds = 1) { error("should never run") }
            }

        assertTrue(
            failure.message.orEmpty().contains(File(lockDir, "device-default.lock").path),
            "message should point at the lock file, but was: ${failure.message}",
        )
        holder.destroyForcibly()
    }

    @Test
    fun `killing a build releases the device`() {
        // The whole reason for an OS lock rather than a file someone has to delete: SIGKILL leaves
        // no chance to clean up, so if the kernel did not release it the device would be locked out
        // until a human noticed.
        val holder = holdFromAnotherProcess(holdMillis = 60_000)
        holder.destroyForcibly().waitFor()

        val startedAt = System.nanoTime()
        val acquiredAfter =
            DeviceMutex.withDevice(timeoutSeconds = 5) { (System.nanoTime() - startedAt) / 1_000_000 }

        assertTrue(acquiredAfter < 1_000, "waited ${acquiredAfter}ms for a device nobody was holding")
    }

    @Test
    fun `a neighbour working through several journeys does not time this one out`() {
        // Each journey takes the device and gives it back, and whoever holds it usually wins the
        // race to take it again — so a waiting build has to tell a busy neighbour from a stuck one,
        // or a long run next door would fail perfectly good builds.
        val holder = holdFromAnotherProcess(holdMillis = 800, turns = 6)

        DeviceMutex.withDevice(timeoutSeconds = 1) {}

        assertEquals(0, holder.waitFor())
    }

    @Test
    fun `the device is free again once the block returns`() {
        repeat(2) { assertEquals(it, DeviceMutex.withDevice(timeoutSeconds = 5) { it }) }
    }

    @Test
    fun `a build on another device does not have to wait`() {
        val holder = holdFromAnotherProcess(holdMillis = 10_000, serial = "some-other-device")

        val startedAt = System.nanoTime()
        val acquiredAfter =
            DeviceMutex.withDevice(timeoutSeconds = 30) { (System.nanoTime() - startedAt) / 1_000_000 }

        // Also what proves the waiting above is the lock and not something incidental: same setup,
        // different serial, no wait.
        assertTrue(acquiredAfter < 1_000, "waited ${acquiredAfter}ms for a device it was not using")
        holder.destroyForcibly()
    }

    /** @return the still-running holder, once it actually has the device. */
    private fun holdFromAnotherProcess(
        holdMillis: Long,
        turns: Int = 1,
        serial: String? = null,
    ): Process {
        val marker = File(lockDir, "held")
        val process =
            ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").path,
                "-D${DeviceMutex.LOCK_DIR_PROPERTY}=${lockDir.path}",
                "-cp",
                System.getProperty("java.class.path"),
                DeviceLockHolder::class.java.name,
                marker.path,
                holdMillis.toString(),
                turns.toString(),
            ).inheritIO()
                .apply { serial?.let { environment()["ANDROID_SERIAL"] = it } }
                .start()

        val deadline = System.currentTimeMillis() + 30_000
        while (!marker.isFile) {
            check(System.currentTimeMillis() < deadline) { "the other JVM never took the device" }
            Thread.sleep(20)
        }
        return process
    }
}
