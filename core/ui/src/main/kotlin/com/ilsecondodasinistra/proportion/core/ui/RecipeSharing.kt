package com.ilsecondodasinistra.proportion.core.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sends a recipe out of the app.
 *
 * Files go through a [FileProvider] on the cache directory, so nothing here needs a storage
 * permission and the shared copy is disposable.
 */
object RecipeSharing {

    fun shareText(context: Context, text: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun shareProportionFile(
        context: Context,
        fileName: String,
        content: String,
        chooserTitle: String,
    ) {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, fileName).apply { writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /** "torta-di-mele.proportion" — safe on every filesystem, still recognisable. */
    fun fileNameFor(title: String): String {
        val slug = title.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "ricetta" }
        return "$slug.proportion"
    }
}
