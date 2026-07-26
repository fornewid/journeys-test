package io.github.fornewid.journeys.engine

import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener
import java.io.PrintWriter

/**
 * Drives the JUnit Platform launcher to run [JourneyTestEngine] and emit standard JUnit XML.
 * Called in-process by the Gradle `journeysTest` task.
 *
 * The engine is registered explicitly rather than through the service loader, so discovery
 * neither depends on the context class loader nor picks up unrelated engines that happen to be
 * on the build's classpath.
 */
object JourneyLauncher {
    /**
     * Runs the discovered journeys and returns how many failed.
     *
     * @param journeyFilter when set, only the journey with this key runs.
     */
    fun run(
        config: JourneyConfig,
        journeyFilter: String? = null,
    ): Long {
        val out = PrintWriter(System.out)
        val summary = SummaryGeneratingListener()

        val launcher =
            LauncherFactory.create(
                LauncherConfig
                    .builder()
                    .enableTestEngineAutoRegistration(false)
                    .addTestEngines(JourneyTestEngine())
                    .build(),
            )
        // Reports from earlier runs go, or a CI job collecting build/journey-results/*.xml would
        // keep reporting results that are no longer produced — a journey that was deleted or
        // renamed would look like it still passes.
        config.reportsDir.listFiles()?.forEach { if (it.name.endsWith(".xml")) it.delete() }
        config.reportsDir.mkdirs()
        launcher.registerTestExecutionListeners(
            LegacyXmlReportGeneratingListener(config.reportsDir.toPath(), out),
            summary,
        )

        val request =
            LauncherDiscoveryRequestBuilder
                .request()
                .apply {
                    configurationParameters(config.toParameters())
                    journeyFilter?.let {
                        selectors(
                            DiscoverySelectors.selectUniqueId(
                                UniqueId
                                    .forEngine(JourneyTestEngine.ENGINE_ID)
                                    .append(JourneyTestEngine.SEGMENT_TYPE, it),
                            ),
                        )
                    }
                }.build()
        launcher.execute(request)

        val result = summary.summary
        result.printTo(out)
        result.printFailuresTo(out)
        if (result.testsFoundCount == 0L) {
            System.err.println("No journeys found in ${config.journeysDir}")
        }
        return result.totalFailureCount
    }
}
