package com.yudistirosaputro.dimock.okhttp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Zero-code start of the engine and wire server. Opt out with
 * `<meta-data android:name="com.yudistirosaputro.dimock.AUTO_INIT" android:value="false"/>` and call [Dimock.init] yourself;
 * change the port with `<meta-data android:name="com.yudistirosaputro.dimock.PORT" android:value="6767"/>`.
 */
class DimockInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return false
        val (autoInit, config) = Dimock.readManifestConfig(context)
        if (autoInit) Dimock.init(context, config)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
