package com.lingua.app.data

import com.lingua.core.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** Only a short-lived user access token is accepted, never a model or administrator key. */
class BackendTeachingService(endpoint: String,private val accessToken: String) : TeachingService {
    private val uri=URI(endpoint.trim())
    init {
        require(uri.scheme=="https" && uri.host!=null && uri.userInfo==null && uri.rawQuery==null && uri.fragment==null)
        require(accessToken.split('.').size==3 && accessToken.length in 30..8192)
    }
    override suspend fun evaluate(language: String,question: TeachingQuestion,answer: String): TeachingFeedback = withContext(Dispatchers.IO) {
        val body=JSONObject().put("language",language).put("questionId",question.id).put("kind",question.kind)
            .put("prompt",question.prompt).put("reference",question.reference).put("explanation",question.explanation)
            .put("answer",answer).toString()
        require(body.toByteArray().size<=16000 && answer.length in 1..1000)
        val requestId=java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
        repeat(2) { attempt ->
            val connection=uri.toURL().openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects=false; connection.connectTimeout=10000; connection.readTimeout=20000
                connection.requestMethod="POST"; connection.doOutput=true
                connection.setRequestProperty("Authorization","Bearer $accessToken")
                connection.setRequestProperty("Content-Type","application/json")
                connection.setRequestProperty("Idempotency-Key",requestId)
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code=connection.responseCode
                if(code in listOf(502,503,504) && attempt==0) { delay(500); return@repeat }
                check(code==200) { "教学服务暂不可用，请检查登录、额度或网络" }
                val result=JSONObject(connection.inputStream.use { readTeachingResponse(it) })
                return@withContext validatedFeedback(TeachingFeedback(result.getString("questionId"),TeachingGrade.valueOf(result.getString("grade")),result.getString("feedback"),result.getString("hint")),question)
            } finally { connection.disconnect() }
        }
        error("教学服务暂不可用")
    }
}

internal fun readTeachingResponse(input: java.io.InputStream): String {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(4096)
    while(true) {
        val count=input.read(buffer)
        if(count<0) break
        require(output.size()+count<=16000) { "教学响应过大" }
        output.write(buffer,0,count)
    }
    return output.toString("UTF-8")
}
