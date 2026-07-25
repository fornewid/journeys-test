package io.github.fornewid.journeys.sample

data class Article(
    val id: String,
    val topic: String,
    val title: String,
    val summary: String,
    val body: String,
)

val topics = listOf(
    "Android",
    "Kotlin",
    "Jetpack Compose",
    "Architecture",
    "Testing",
    "Performance",
)

val articles = listOf(
    Article(
        id = "compose",
        topic = "Jetpack Compose",
        title = "Build better apps with Jetpack Compose",
        summary = "A practical guide to state, layouts, and reusable UI.",
        body = "Compose makes Android UI development faster. " +
            "Build small, testable components and keep state close to the screen that owns it.",
    ),
    Article(
        id = "architecture",
        topic = "Architecture",
        title = "A compact guide to app architecture",
        summary = "Keep dependencies pointing inward and responsibilities focused.",
        body = "Clear boundaries make Android applications easier to test and evolve.",
    ),
    Article(
        id = "testing",
        topic = "Testing",
        title = "Test user journeys that matter",
        summary = "Describe critical flows in language shared by product and engineering.",
        body = "Journey tests complement focused unit and UI tests with end-to-end confidence.",
    ),
)
