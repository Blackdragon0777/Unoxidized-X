package com.unoxidized.shared
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/** Signature-protected on-device sync. Only apps signed with the same key (the Unoxidized family) can read it. */
abstract class SyncProvider : ContentProvider() {
    abstract fun data(c: Context): Bundle
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = if (method == "status") context?.let { data(it) } else null
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
object SyncClient { fun pull(c: Context, authority: String): Bundle? = try { c.contentResolver.call(Uri.parse("content://$authority"), "status", null, null) } catch (e: Exception) { null } }
