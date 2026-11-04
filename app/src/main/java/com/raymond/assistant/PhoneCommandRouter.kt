package com.raymond.assistant
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.text.DecimalFormat
import java.util.Locale
object PhoneCommandRouter {
 fun handle(context:Context,raw:String):String? {
  val t=raw.lowercase(Locale.getDefault()).trim().removePrefix("hey raymond").removePrefix("raymond").trim()
  if(t.isBlank())return "I'm listening. What would you like me to do?"
  if(t.contains("end call")||t.contains("hang up")||t.contains("disconnect the call"))return "Android restricts ending active calls. RAYMOND would need default-phone-app integration and protected in-call APIs."
  if(t.startsWith("call ")){val n=t.removePrefix("call ").filter{it.isDigit()||it=='+'};if(n.isNotBlank()){context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return "I've opened the dialer. Review the number and tap Call when you're ready."};return "Please say the number you want to call."}
  if(t.contains("open settings")||t=="settings"){context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return "Opening Android settings."}
  val apps=mapOf("youtube" to "com.google.android.youtube","whatsapp" to "com.whatsapp","calculator" to "com.google.android.calculator","camera" to "com.android.camera","chrome" to "com.android.chrome","gmail" to "com.google.android.gm","maps" to "com.google.android.apps.maps","play store" to "com.android.vending","phone" to "com.google.android.dialer","messages" to "com.google.android.apps.messaging")
  val app=apps.keys.firstOrNull{t==it||t=="open $it"||t.startsWith("open $it ")}
  if(app!=null){val launch=context.packageManager.getLaunchIntentForPackage(apps.getValue(app))?:return "I couldn't find $app on this phone.";launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch);return "Opening $app."}
  if(t.startsWith("calculate ")||t.startsWith("what is ")||t.startsWith("what's ")){
   val expr=t.removePrefix("calculate ").removePrefix("what is ").removePrefix("what's ").replace(" multiplied by ","*").replace(" times ","*").replace(" divided by ","/").replace(" plus ","+").replace(" minus ","-").replace(" over ","/")
   if(expr.any{it.isDigit()})return try{"The answer is "+DecimalFormat("0.########").format(MathEvaluator(expr).evaluate())}catch(e:Exception){e.message?:"I couldn't calculate that."}
  }
  return null
 }
}