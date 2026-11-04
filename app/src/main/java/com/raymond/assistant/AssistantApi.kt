package com.raymond.assistant
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
object AssistantApi {
 private const val ENDPOINT="https://raymond-ai-router-gqzlqw.v2.appdeploy.ai/api/assistant/chat"
 private val executor=Executors.newSingleThreadExecutor()
 private val history=mutableListOf<Pair<String,String>>()
 fun ask(message:String, callback:(Result<String>)->Unit) { executor.execute {
  try {
   val token=BuildConfig.RAYMOND_ACCESS_TOKEN
   if(token.isBlank()) throw IllegalStateException("AI connection not configured. Add the RAYMOND_ACCESS_TOKEN GitHub Actions secret, then rebuild.")
   val c=URL(ENDPOINT).openConnection() as HttpURLConnection
   c.requestMethod="POST"; c.connectTimeout=15000; c.readTimeout=30000; c.doOutput=true
   c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Authorization","Bearer $token")
   val recent=JSONArray()
   synchronized(history){ history.takeLast(12).forEach{(role,content)->recent.put(JSONObject().put("role",role).put("content",content))} }
   val body=JSONObject().put("message",message).put("provider","auto").put("history",recent)
   c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
   val code=c.responseCode; val stream=if(code in 200..299)c.inputStream else c.errorStream
   val response=stream.bufferedReader().use{it.readText()}; c.disconnect()
   if(code !in 200..299) throw IllegalStateException("RAYMOND server returned HTTP $code.")
   val answer=JSONObject(response).optString("answer").trim()
   if(answer.isBlank()) throw IllegalStateException("The AI service returned an empty response.")
   synchronized(history){history.add("user" to message);history.add("assistant" to answer);while(history.size>12)history.removeAt(0)}
   callback(Result.success(answer))
  } catch(e:Exception){callback(Result.failure(e))}
 } }
}