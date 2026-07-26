package io.github.fornewid.journeys.engine

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Keeps one agent at a time on the device, across every build on the machine.
 *
 * Gradle's build service serializes tasks within one invocation, but two terminals — or CI and a
 * local run, or two checkouts of the same project — are separate processes it knows nothing about.
 * An OS file lock covers those: the kernel ties it to the process, so it is released even if a
 * build is killed, and it lives outside any project directory so different checkouts contend for
 * the device rather than for their own build directories.
 *
 * The lock is per device serial, so several devices work in parallel as long as each run sets
 * `ANDROID_SERIAL` — which adb requires anyway once more than one is attached.
 */
internal object DeviceMutex {
    /** A file lock belongs to the whole JVM, so threads inside one build queue up here first. */
    private val inProcess = ReentrantLock()

    /**
     * Holds the device for the duration of [block].
     *
     * @param timeoutSeconds how long to wait for another build to let go. One agent run never holds
     *   the lock longer than this, so exceeding it means the holder is stuck rather than busy.
     * @param onWait called once if the device turns out to be taken, to explain the wait.
     */
    fun <T> withDevice(
        timeoutSeconds: Long,
        onWait: (File) -> Unit = {},
        block: () -> T,
    ): T {
        val lockFile = lockFile()
        inProcess.lock()
        try {
            RandomAccessFile(lockFile, "rw").use { file ->
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                var lock = file.channel.tryLock()
                if (lock == null) onWait(lockFile)
                while (lock == null) {
                    check(System.nanoTime() < deadline) {
                        "another build has been driving the device for more than ${timeoutSeconds}s. " +
                            "Wait for it to finish, or delete $lockFile if nothing is running."
                    }
                    Thread.sleep(POLL_MILLIS)
                    lock = file.channel.tryLock()
                }
                try {
                    return block()
                } finally {
                    lock.release()
                }
            }
        } finally {
            inProcess.unlock()
        }
    }

    private fun lockFile(): File {
        val serial = System.getenv("ANDROID_SERIAL")?.takeIf { it.isNotBlank() } ?: "default"
        val directory = File(System.getProperty(LOCK_DIR_PROPERTY) ?: defaultLockDir()).apply { mkdirs() }
        return File(directory, "device-${serial.replace(UNSAFE, "_")}.lock")
    }

    private fun defaultLockDir(): String = File(System.getProperty("user.home"), ".journeys-test").path

    /** Where the lock files live. Only meant for tests, which must not contend with real runs. */
    const val LOCK_DIR_PROPERTY = "journeys.deviceLockDir"

    private const val POLL_MILLIS = 500L
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")
}
