package org.fossify.phone.recording

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json

/** Pure request shaping for the Google Drive v3 REST API. Does no I/O. */
object DriveApi {
    const val FOLDER_MIME = "application/vnd.google-apps.folder"

    /** Drive `q` to find a non-trashed folder by exact name. */
    fun folderQuery(name: String): String =
        "mimeType='$FOLDER_MIME' and name='$name' and trashed=false"

    /** JSON body to create a folder with the given name. */
    fun createFolderBody(name: String): String = Json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "name" to JsonPrimitive(name),
                "mimeType" to JsonPrimitive(FOLDER_MIME),
            ),
        ),
    )

    /** JSON metadata part for a multipart upload into [parentId]. */
    fun uploadMetadata(name: String, parentId: String): String = Json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "name" to JsonPrimitive(name),
                "parents" to JsonArray(listOf(JsonPrimitive(parentId))),
            ),
        ),
    )
}
