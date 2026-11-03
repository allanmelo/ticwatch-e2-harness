package com.allanmelo.harness

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AgentStatus(
    val agent: String = "HERMES",
    val state: String = "IDLE",
    val lastMsg: String = "Ready",
    val tokens: Int = 0
)

class HarnessNetClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getStatus(baseUrl: String, callback: (Result<AgentStatus>) -> Unit) {
        val cleanUrl = baseUrl.trimEnd('/') + "/status"
        val request = Request.Builder()
            .url(cleanUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${response.code}: $body")))
                        return
                    }
                    val json = JSONObject(body)
                    val status = AgentStatus(
                        agent = json.optString("agent", "HERMES"),
                        state = json.optString("state", "IDLE"),
                        lastMsg = json.optString("last_msg", "OK"),
                        tokens = json.optInt("tokens", 0)
                    )
                    callback(Result.success(status))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                } finally {
                    response.close()
                }
            }
        })
    }

    fun sendVoice(baseUrl: String, audioFile: File, callback: (Result<AgentStatus>) -> Unit) {
        val cleanUrl = baseUrl.trimEnd('/') + "/voice"
        val mediaType = "audio/mp4".toMediaType()
        val body = audioFile.asRequestBody(mediaType)

        val request = Request.Builder()
            .url(cleanUrl)
            .post(body)
            .addHeader("Content-Type", "audio/mp4")
            .addHeader("X-File-Name", audioFile.name)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val respBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${response.code}: $respBody")))
                        return
                    }
                    val json = JSONObject(respBody)
                    val status = AgentStatus(
                        agent = json.optString("agent", "HERMES"),
                        state = json.optString("state", "RUNNING"),
                        lastMsg = json.optString("last_msg", "Audio processed"),
                        tokens = json.optInt("tokens", 0)
                    )
                    callback(Result.success(status))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                } finally {
                    response.close()
                }
            }
        })
    }
}
