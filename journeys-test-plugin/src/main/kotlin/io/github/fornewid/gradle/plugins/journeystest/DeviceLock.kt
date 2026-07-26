package io.github.fornewid.gradle.plugins.journeystest

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Serializes every task that drives the device.
 *
 * Journeys run against one connected device, but Gradle runs tasks from different projects in
 * parallel: in a multi-module build two agents would otherwise tap and swipe on the same screen at
 * the same time, each backgrounding the other's app. Registering this service with
 * `maxParallelUsages = 1` and declaring it with `usesService` makes Gradle run them one after
 * another, without giving up parallelism for the rest of the build.
 */
abstract class DeviceLock : BuildService<BuildServiceParameters.None>
