package io.github.fornewid.journeys.sample

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun NowInAndroidApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.Onboarding) }
    var selectedTopic by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArticleId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = destination == Destination.Article) {
        destination = Destination.Feed
        selectedArticleId = null
    }

    when (destination) {
        Destination.Onboarding -> OnboardingScreen(
            selectedTopic = selectedTopic,
            onTopicSelected = { selectedTopic = it },
            onDone = { destination = Destination.Feed },
        )

        Destination.Feed -> FeedScreen(
            onArticleSelected = { articleId ->
                selectedArticleId = articleId
                destination = Destination.Article
            },
        )

        Destination.Article -> ArticleScreen(
            article = articles.first { it.id == selectedArticleId },
            onBack = {
                destination = Destination.Feed
                selectedArticleId = null
            },
        )
    }
}

@Composable
private fun OnboardingScreen(
    selectedTopic: String?,
    onTopicSelected: (String) -> Unit,
    onDone: () -> Unit,
) {
    AppScaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(
                    text = "What are you interested in?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(
                    text = "Choose a topic to personalize your For you feed.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(topics.chunked(2)) { rowTopics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowTopics.forEach { topic ->
                        FilterChip(
                            selected = selectedTopic == topic,
                            onClick = { onTopicSelected(topic) },
                            label = { Text(topic) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTopics.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Button(
                    onClick = onDone,
                    enabled = selectedTopic != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun FeedScreen(onArticleSelected: (String) -> Unit) {
    AppScaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "For you",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(articles, key = { it.id }) { article ->
                ArticleCard(
                    article = article,
                    onClick = { onArticleSelected(article.id) },
                )
            }
        }
    }
}

@Composable
private fun ArticleCard(
    article: Article,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = article.topic,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleScreen(
    article: Article,
    onBack: () -> Unit,
) {
    AppScaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        ) {
            item {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = article.topic,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = article.body,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AppScaffold(content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = "Now in Android",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        content = content,
    )
}

private enum class Destination {
    Onboarding,
    Feed,
    Article,
}

@Preview(showBackground = true)
@Composable
private fun NowInAndroidAppPreview() {
    SampleTheme {
        NowInAndroidApp()
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    SampleTheme {
        OnboardingScreen(
            selectedTopic = "Android",
            onTopicSelected = {},
            onDone = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenPreview() {
    SampleTheme {
        FeedScreen(onArticleSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleCardPreview() {
    SampleTheme {
        ArticleCard(
            article = articles.first(),
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleScreenPreview() {
    SampleTheme {
        ArticleScreen(
            article = articles.first(),
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppScaffoldPreview() {
    SampleTheme {
        AppScaffold { innerPadding ->
            Text(
                text = "Preview content",
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
