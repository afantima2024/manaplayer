package com.macarmusic.app.data

import android.net.Uri
import android.provider.DocumentsContract

internal object DocumentUris {
    fun isTree(uri: Uri): Boolean = uri.scheme == "content" && !uri.authority.isNullOrBlank() &&
        uri.pathSegments.let { it.size >= 2 && it[0] == "tree" && it[1].isNotEmpty() }

    fun grant(uri: Uri): Uri = if (isTree(uri))
        DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri)) else uri

    fun rootId(uri: Uri): String = if (uri.pathSegments.let { it.size >= 4 && it[2] == "document" })
        DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)

    fun document(uri: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(uri, rootId(uri))
}
