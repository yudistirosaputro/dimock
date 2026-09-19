package com.yudistirosaputro.dimock.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.yudistirosaputro.dimock.okhttp.Dimock
import com.yudistirosaputro.dimock.ui.notification.DimockNotification

/**
 * Attaches the notification to the engine. Runs after `DimockInitProvider` (initOrder 90 > 80) when the host
 * auto-inits; when the host opted out and calls `Dimock.init` itself later, [Dimock.onInit] fires then instead.
 */
class DimockUiInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return false
        Dimock.onInit { _, engine ->
            if (Dimock.config?.showNotification != false) DimockNotification.install(context, engine)
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
