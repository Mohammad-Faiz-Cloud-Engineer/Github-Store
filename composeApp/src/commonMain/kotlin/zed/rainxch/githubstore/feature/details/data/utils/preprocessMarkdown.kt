package zed.rainxch.githubstore.feature.details.data.utils

fun preprocessMarkdown(markdown: String, baseUrl: String): String {
    return markdown.replace(Regex("!\\[([^\\]]*)\\]\\((?!https?://)([^)]+)\\)")) { match ->
        val alt = match.groupValues[1]
        var relativePath = match.groupValues[2].trim()
        if (relativePath.startsWith("./")) relativePath = relativePath.removePrefix("./")
        if (relativePath.startsWith("/")) relativePath = relativePath.removePrefix("/")
        "![$alt]($baseUrl$relativePath)"
    }
}