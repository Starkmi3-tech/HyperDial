package org.fossify.phone.recording

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder

/**
 * Google Sign-In + Drive v3 REST on the drive.file scope only. Uploads land in
 * a "HyperDial Recordings" folder. All methods block and run off the main thread
 * (from the upload worker). Request strings come from [DriveApi]; this class does
 * the auth + network I/O.
 */
class DriveClient(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient(),
) {
    fun hasAccount(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun signInIntent(): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    fun signOut() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, options).signOut()
    }

    /** Returns the Drive id of the recordings folder, creating it if absent. */
    fun ensureFolder(): String {
        val token = accessToken()
        val query = URLEncoder.encode(DriveApi.folderQuery(FOLDER_NAME), "UTF-8")
        val listReq = Request.Builder()
            .url("$DRIVE_API/files?q=$query&spaces=drive&fields=files(id)")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(listReq).execute().use { resp ->
            firstFileId(resp.body?.string().orEmpty())?.let { return it }
        }
        val createReq = Request.Builder()
            .url("$DRIVE_API/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(DriveApi.createFolderBody(FOLDER_NAME).toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(createReq).execute().use { resp ->
            return fileId(resp.body?.string().orEmpty())
                ?: error("Drive folder create failed: ${resp.code}")
        }
    }

    /** Uploads [file] into the recordings folder; returns its Drive file id. */
    fun upload(file: File, displayName: String): String {
        val token = accessToken()
        val folderId = ensureFolder()
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(DriveApi.uploadMetadata(displayName, folderId).toRequestBody(JSON_MEDIA))
            .addPart(file.asRequestBody(AUDIO_MEDIA))
            .build()
        val req = Request.Builder()
            .url("$UPLOAD_API/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        http.newCall(req).execute().use { resp ->
            return fileId(resp.body?.string().orEmpty())
                ?: error("Drive upload failed: ${resp.code}")
        }
    }

    private fun accessToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: error("Not signed in to Google")
        return GoogleAuthUtil.getToken(context, account, "oauth2:$DRIVE_FILE_SCOPE")
    }

    private fun fileId(json: String): String? = runCatching {
        Json.parseToJsonElement(json).jsonObject["id"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun firstFileId(json: String): String? = runCatching {
        Json.parseToJsonElement(json).jsonObject["files"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        const val FOLDER_NAME = "HyperDial Recordings"
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private val JSON_MEDIA = "application/json; charset=UTF-8".toMediaType()
        private val AUDIO_MEDIA = "audio/mp4".toMediaType()
    }
}
