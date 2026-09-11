/*
 * Copyright (c) 2026 Sulee7z <94352968+sulee7z@users.noreply.github.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.util.getParcelableExtraSafe
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.takeIfNotEmpty

/**
 * Share-menu entry (MT Manager style "locate file location"): resolves the shared file's
 * real filesystem path and opens the file list at its parent directory with the file
 * selected. URIs are resolved from the document ID for the platform external-storage
 * provider, or from the provider's _data column (readable here because the app holds
 * All-Files-Access); share senders that offer neither cannot be located.
 */
class LocateFileActivity : AppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = resolveLocatedPath(intent)
        val directory = path?.parent
        if (directory == null) {
            showToast(R.string.locate_file_error)
            finish()
            return
        }
        startActivity(
            FileListActivity.createViewIntent(directory)
                .putExtra(FileListActivity.EXTRA_LOCATE_FILE_NAME, path.fileName.toString())
        )
        finish()
    }

    private fun resolveLocatedPath(intent: Intent): Path? {
        val uri = intent.getParcelableExtraSafe<Uri>(Intent.EXTRA_STREAM) ?: return null
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE, null ->
                uri.path?.takeIfNotEmpty()?.let { runCatching { Paths.get(it) }.getOrNull() }
            ContentResolver.SCHEME_CONTENT -> resolveContentUri(uri)
            else -> null
        }
    }

    private fun resolveContentUri(uri: Uri): Path? {
        documentsUriPath(uri)?.let { return it }
        return queryDataPath(uri)
    }

    /**
     * content://com.android.externalstorage.documents/document/primary%3ADownload%2Ffoo.txt
     * → /storage/emulated/0/Download/foo.txt (the standard system file-picker scheme).
     */
    private fun documentsUriPath(uri: Uri): Path? {
        if (uri.authority != DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY) {
            return null
        }
        val segments = uri.pathSegments
        val documentId = when (segments.size) {
            2 -> if (segments[0] == "document") segments[1] else null
            4 -> if (segments[0] == "tree" && segments[2] == "document") segments[3] else null
            else -> null
        } ?: return null
        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2) {
            return null
        }
        val (volume, relativePath) = parts
        val root = when (volume) {
            DocumentsContractCompat.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID, "home" ->
                Environment.getExternalStorageDirectory().absolutePath
            else -> "/storage/$volume"
        }
        return runCatching {
            if (relativePath.isEmpty()) Paths.get(root) else Paths.get(root, relativePath)
        }.getOrNull()
    }

    /** Generic provider fallback: read the real path from the _data column. */
    private fun queryDataPath(uri: Uri): Path? = try {
        contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIfNotEmpty()?.let {
                        runCatching { Paths.get(it) }.getOrNull()
                    }
                } else {
                    null
                }
            }
    } catch (e: Exception) {
        null
    }
}
