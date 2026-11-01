package org.fossify.phone.recording

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveApiTest {
    private val folderMime = "application/vnd.google-apps.folder"

    @Test
    fun folder_query_matches_name_and_type() {
        val q = DriveApi.folderQuery("HyperDial Recordings")
        assertTrue(q, q.contains("mimeType='$folderMime'"))
        assertTrue(q, q.contains("name='HyperDial Recordings'"))
        assertTrue(q, q.contains("trashed=false"))
    }

    @Test
    fun create_folder_body_has_folder_mime() {
        val obj = Json.parseToJsonElement(DriveApi.createFolderBody("HyperDial Recordings")).jsonObject
        assertEquals("HyperDial Recordings", obj["name"]?.jsonPrimitive?.content)
        assertEquals(folderMime, obj["mimeType"]?.jsonPrimitive?.content)
    }

    @Test
    fun upload_metadata_sets_name_and_parent() {
        val obj = Json.parseToJsonElement(DriveApi.uploadMetadata("call.m4a", "folder123")).jsonObject
        assertEquals("call.m4a", obj["name"]?.jsonPrimitive?.content)
        assertEquals("folder123", obj["parents"]?.jsonArray?.first()?.jsonPrimitive?.content)
    }
}
