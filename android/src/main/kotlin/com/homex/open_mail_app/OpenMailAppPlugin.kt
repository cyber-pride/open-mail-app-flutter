package com.homex.open_mail_app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.NonNull
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class OpenMailAppPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var applicationContext: Context

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "open_mail_app")
        channel.setMethodCallHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "openMailApp" -> result.success(emailAppIntent(call.argument("nativePickerTitle") ?: "Choose Mail App"))
            "openSpecificMailApp" -> call.argument<String>("name")?.let { result.success(specificEmailAppIntent(it)) }
            "composeNewEmailInMailApp" -> call.argument<String>("emailContent")?.let {
                result.success(composeNewEmailAppIntent(call.argument("nativePickerTitle") ?: "Compose Email", it))
            }
            "composeNewEmailInSpecificMailApp" -> {
                val name = call.argument<String>("name")
                val content = call.argument<String>("emailContent")
                if (name != null && content != null) {
                    result.success(composeNewEmailInSpecificEmailAppIntent(name, content))
                } else {
                    result.success(false)
                }
            }
            "getMainApps" -> result.success(Gson().toJson(getInstalledMailApps()))
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun emailAppIntent(@NonNull chooserTitle: String): Boolean {
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activities = packageManager.queryIntentActivities(emailIntent, 0)

        if (activities.isNotEmpty()) {
            val intents = activities.mapNotNull {
                packageManager.getLaunchIntentForPackage(it.activityInfo.packageName)
            }.toTypedArray()

            val chooserIntent = Intent.createChooser(intents.first(), chooserTitle).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            applicationContext.startActivity(chooserIntent)
            return true
        }
        return false
    }

    private fun composeNewEmailAppIntent(@NonNull chooserTitle: String, @NonNull contentJson: String): Boolean {
        val emailContent = Gson().fromJson(contentJson, EmailContent::class.java)
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, emailContent.to.toTypedArray())
            putExtra(Intent.EXTRA_CC, emailContent.cc.toTypedArray())
            putExtra(Intent.EXTRA_BCC, emailContent.bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, emailContent.subject)
            putExtra(Intent.EXTRA_TEXT, emailContent.body)
        }

        val packageManager = applicationContext.packageManager
        val activities = packageManager.queryIntentActivities(emailIntent, 0)
        if (activities.isNotEmpty()) {
            val intents = activities.mapNotNull {
                Intent(emailIntent).setClassName(it.activityInfo.packageName, it.activityInfo.name)
            }.toTypedArray()

            val chooserIntent = Intent.createChooser(intents.first(), chooserTitle).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            applicationContext.startActivity(chooserIntent)
            return true
        }
        return false
    }

    private fun specificEmailAppIntent(name: String): Boolean {
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activities = packageManager.queryIntentActivities(emailIntent, 0)

        activities.firstOrNull { it.loadLabel(packageManager).toString() == name }?.activityInfo?.packageName?.let {
            packageManager.getLaunchIntentForPackage(it)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                applicationContext.startActivity(this)
                return true
            }
        }
        return false
    }

    private fun composeNewEmailInSpecificEmailAppIntent(@NonNull name: String, @NonNull contentJson: String): Boolean {
        val packageManager = applicationContext.packageManager
        val emailContent = Gson().fromJson(contentJson, EmailContent::class.java)
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))

        val activities = packageManager.queryIntentActivities(emailIntent, 0)
        val specificApp = activities.firstOrNull { it.loadLabel(packageManager).toString() == name } ?: return false

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            setClassName(specificApp.activityInfo.packageName, specificApp.activityInfo.name)
            putExtra(Intent.EXTRA_EMAIL, emailContent.to.toTypedArray())
            putExtra(Intent.EXTRA_CC, emailContent.cc.toTypedArray())
            putExtra(Intent.EXTRA_BCC, emailContent.bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, emailContent.subject)
            putExtra(Intent.EXTRA_TEXT, emailContent.body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        applicationContext.startActivity(intent)
        return true
    }

    private fun getInstalledMailApps(): List<App> {
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        return packageManager.queryIntentActivities(emailIntent, 0)
            .map { App(it.loadLabel(packageManager).toString()) }
    }
}

data class App(@SerializedName("name") val name: String)

data class EmailContent(
    @SerializedName("to") val to: List<String>,
    @SerializedName("cc") val cc: List<String>,
    @SerializedName("bcc") val bcc: List<String>,
    @SerializedName("subject") val subject: String,
    @SerializedName("body") val body: String
)
