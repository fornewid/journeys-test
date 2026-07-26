package io.github.fornewid.journeys.engine

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
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
     * @param timeoutSeconds how long to wait without anyone letting go of the device. A neighbour
     *   working through several journeys keeps renewing this, so it only runs out on a stuck agent.
     * @param maxWaitSeconds how long to queue behind other builds in total, however busy they are.
     * @param onWait called when the device turns out to be taken and every [NOTICE_SECONDS] after,
     *   with how long the wait has run and who is holding it, so it never looks like a hang.
     */
    fun <T> withDevice(
        timeoutSeconds: Long,
        maxWaitSeconds: Long = Long.MAX_VALUE,
        onWait: (lockFile: File, waitedSeconds: Long, holder: String) -> Unit = { _, _, _ -> },
        block: () -> T,
    ): T {
        val lockFile = lockFile()
        inProcess.lock()
        try {
            open(lockFile).use { file ->
                val lock = file.acquire(lockFile, timeoutSeconds, maxWaitSeconds, onWait)
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

    /**
     * Waits for the device, giving up only if nobody has taken a turn for [timeoutSeconds].
     *
     * The deadline tracks turns rather than total waiting because whoever holds the device releases
     * it between journeys and usually wins the race to take it again — a build with several
     * journeys would otherwise starve everyone else out and fail their builds while working
     * perfectly well itself. A turn that never ends, on the other hand, means an agent outlived the
     * timeout that was supposed to kill it.
     *
     * A queue of busy neighbours is otherwise waited out until [maxWaitSeconds], which is about
     * being willing to queue rather than about anything being wrong — the two give different
     * advice when they run out. What keeps the waiting from looking like a hang is [onWait], which
     * keeps saying who has the device and for how long.
     */
    private fun RandomAccessFile.acquire(
        lockFile: File,
        timeoutSeconds: Long,
        maxWaitSeconds: Long,
        onWait: (File, Long, String) -> Unit,
    ): FileLock {
        channel.tryLock()?.let { lock -> return lock.also { stamp() } }

        val startedAt = System.nanoTime()
        var holder = holder()
        var stallDeadline = startedAt + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        var nextNotice = 0L
        while (true) {
            val waited = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt)
            if (waited >= nextNotice) {
                onWait(lockFile, waited, holder)
                nextNotice = waited + NOTICE_SECONDS
            }
            Thread.sleep(POLL_MILLIS)
            channel.tryLock()?.let { lock -> return lock.also { stamp() } }
            val current = holder()
            if (current != holder) {
                holder = current
                stallDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            }
            check(System.nanoTime() < stallDeadline) {
                "no build has let go of the device for ${timeoutSeconds}s (held by $holder). " +
                    "Stop it, or delete $lockFile if nothing is running."
            }
            // Compared as elapsed rather than against a deadline, so a very large limit cannot
            // overflow into the past.
            check(System.nanoTime() - startedAt < TimeUnit.SECONDS.toNanos(maxWaitSeconds)) {
                "gave up after queueing ${maxWaitSeconds}s for the device (now held by $holder). " +
                    "Other builds kept using it, so nothing is stuck — raise deviceWaitSeconds to " +
                    "wait longer."
            }
        }
    }

    /** Marks this turn, so anyone waiting can tell a busy neighbour from a stuck one. */
    private fun RandomAccessFile.stamp() {
        setLength(0)
        seek(0)
        write("pid ${ProcessHandle.current().pid()} since ${System.currentTimeMillis()}".toByteArray())
    }

    private fun RandomAccessFile.holder(): String =
        try {
            seek(0)
            ByteArray(length().toInt().coerceAtMost(MAX_STAMP_BYTES))
                .also { readFully(it) }
                .decodeToString()
        } catch (_: IOException) {
            "" // mid-write, or not stamped yet; the next poll sees it
        }

    private fun open(lockFile: File): RandomAccessFile =
        try {
            RandomAccessFile(lockFile, "rw")
        } catch (e: IOException) {
            throw IllegalStateException(
                "cannot claim the device: $lockFile is not writable. " +
                    "Set -D$LOCK_DIR_PROPERTY to a directory this build can write to.",
                e,
            )
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

    /** How often a build that is waiting says so. Often enough to look alive, rare enough to skim. */
    private const val NOTICE_SECONDS = 30L
    private const val MAX_STAMP_BYTES = 64
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")
}
