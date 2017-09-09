package net.donething.android.flowwall

import android.app.Application
import android.preference.PreferenceManager
import android.provider.Settings
import com.tencent.bugly.crashreport.CrashReport

// Created by Donething on 2017-09-10.

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Bugly统计 初始化
        CrashReport.initCrashReport(applicationContext, "8cd3298815", false)
        CrashReport.setUserId(PreferenceManager.getDefaultSharedPreferences(this)
                .getString(CommHelper.PHONE_NUM, Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)))
    }
}