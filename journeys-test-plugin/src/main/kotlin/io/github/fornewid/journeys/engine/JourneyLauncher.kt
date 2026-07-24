package io.github.fornewid.journeys.engine

import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener
import java.io.PrintWriter
import java.nio.file.Path

/**
 * Drives the JUnit Platform launcher to run [JourneyTestEngine] and emit standard JUnit XML.
 * Called in-process by the Gradle `journeysTest` task. Configured via [EngineConfig].
 */
object JourneyLauncher {

    /** Runs all discovered journeys, writes the JUnit XML report, and returns the failure count. */
    fun run(): Long {
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
        return s.totalFailureCount
    }
}
