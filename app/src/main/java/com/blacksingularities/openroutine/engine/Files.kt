package com.blacksingularities.openroutine.engine

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** Pliki w folderach wybranych przez użytkownika (Storage Access Framework). */
object Files {
    data class Doc(val id: String, val uri: Uri, val name: String, val mime: String, val modified: Long, val size: Long) {
        val isDir: Boolean get() = mime == Document.MIME_TYPE_DIR
    }

    fun list(ctx: Context, tree: String): List<Doc> {
        if (tree.isBlank()) error("Nie wybrano folderu")
        val treeUri = Uri.parse(tree)
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE)
        val out = mutableListOf<Doc>()
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                out += Doc(
                    id = id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    name = c.getString(1).orEmpty(),
                    mime = c.getString(2) ?: "application/octet-stream",
                    modified = if (c.isNull(3)) 0L else c.getLong(3),
                    size = if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
        } ?: error("Brak dostępu do folderu – wybierz go ponownie")
        return out
    }

    fun matching(ctx: Context, tree: String, pattern: String, olderThanDays: Int = 0): List<Doc> {
        val cutoff = if (olderThanDays > 0) System.currentTimeMillis() - olderThanDays * 86_400_000L else Long.MAX_VALUE
        return list(ctx, tree).filter { !it.isDir && com.blacksingularities.openroutine.model.Fmt.glob(pattern, it.name) && it.modified <= cutoff }
    }

    private fun treeDocUri(tree: String): Uri {
        val treeUri = Uri.parse(tree)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    }

    fun copy(ctx: Context, doc: Doc, targetTree: String): Uri {
        if (targetTree.isBlank()) error("Nie wybrano folderu docelowego")
        val resolver = ctx.contentResolver
        val created = DocumentsContract.createDocument(resolver, treeDocUri(targetTree), doc.mime, doc.name)
            ?: error("Nie można utworzyć pliku ${doc.name}")
        resolver.openInputStream(doc.uri).use { input ->
            resolver.openOutputStream(created, "w").use { out ->
                if (input == null || out == null) error("Nie można skopiować ${doc.name}")
                input.copyTo(out)
            }
        }
        return created
    }

    fun delete(ctx: Context, doc: Doc): Boolean = DocumentsContract.deleteDocument(ctx.contentResolver, doc.uri)

    /** Zapis tekstu do pliku w folderze; tworzy plik, gdy go nie ma. */
    fun writeText(ctx: Context, tree: String, name: String, text: String, append: Boolean) {
        val resolver = ctx.contentResolver
        val existing = list(ctx, tree).firstOrNull { !it.isDir && it.name == name }
        val uri = existing?.uri
            ?: DocumentsContract.createDocument(resolver, treeDocUri(tree), "text/plain", name)
            ?: error("Nie można utworzyć pliku $name")
        val line = if (append) text + "\n" else text
        if (append && existing != null) {
            // Nie każdy dostawca obsługuje tryb „wa” – wtedy dopisujemy przez odczyt i nadpisanie.
            val ok = runCatching { resolver.openOutputStream(uri, "wa")!!.use { it.write(line.toByteArray()) } }.isSuccess
            if (ok) return
            val old = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
            resolver.openOutputStream(uri, "wt")!!.use { it.write((old + line).toByteArray()) }
        } else {
            resolver.openOutputStream(uri, "wt")!!.use { it.write(line.toByteArray()) }
        }
    }
}
