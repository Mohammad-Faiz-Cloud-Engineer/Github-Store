package zed.rainxch.githubstore.feature.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.githubstore.feature.details.domain.model.RepoStats
import zed.rainxch.githubstore.feature.details.presentation.components.StatItem

fun LazyListScope.stats(
    repoStats: RepoStats,
) {
    item {
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatItem(
                label = "Forks",
                stat = repoStats.forks,
                modifier = Modifier.weight(1.5f)
            )

            StatItem(
                label = "Stars",
                stat = repoStats.stars,
                modifier = Modifier.weight(2f)
            )

            StatItem(
                label = "Issues",
                stat = repoStats.openIssues,
                modifier = Modifier.weight(1f)
            )
        }
    }
}