package io.github.fornewid.journeys.engine

import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Entry point for the Gradle `journeysTest` task (JavaExec).
 *
 * Drives the JUnit Platform launcher directly — rather than Gradle's class-oriented Test task —
 * to run [JourneyTestEngine] and emit standard JUnit XML. Configured via [EngineConfig].
 */
object JourneyLauncher {

    @JvmStatic
    fun main(args: Array<String>) {
        val out = PrintWriter(System.out)
        val summary = SummaryGeneratingListener()

        val launcher = LauncherFactory.create()
        launcher.registerTestExecutionListeners(
            LegacyXmlReportGeneratingListener(Path.of(EngineConfig.reportsDir()), out),
            summary,
        )
        launcher.execute(LauncherDiscoveryRequestBuilder.request().build())

        val s = summary.summary
        s.printTo(out)
        s.printFailuresTo(out)
        if (s.containersFoundCount == 0L && s.testsFoundCount == 0L) {
            System.err.println("No journeys found. journeys.dir=${EngineConfig.journeysDir()}")
        }
        if (s.totalFailureCount > 0) exitProcess(1)
    }
}
