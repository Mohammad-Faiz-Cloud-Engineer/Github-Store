package zed.rainxch.githubstore.feature.details.presentation.components.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zed.rainxch.githubstore.feature.details.presentation.DetailsState

fun LazyListScope.logs(state: DetailsState) {
    item {
        HorizontalDivider()

        Text(
            text = "Install logs",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp),
            fontWeight = FontWeight.Bold,
        )
    }

    items(state.installLogs) { log ->
        Text(
            text = "> ${log.result}: ${log.assetName}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontStyle = FontStyle.Italic
            ),
            color = if (log.result.startsWith("Error")) {
                MaterialTheme.colorScheme.error
            } else MaterialTheme.colorScheme.outline
        )
    }
}