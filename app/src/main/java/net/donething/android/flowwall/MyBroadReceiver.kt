package net.donething.android.flowwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast

/**
 * Created by donet on 17-9-5.
 */
class MyBroadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val sharedPre = PreferenceManager.getDefaultSharedPreferences(ctx)
        // 开启流量跳点查询服务
        if (intent.action == "android.intent.action.BOOT_COMPLETED" && sharedPre.getBoolean(CommHelper.IS_BOOT_START, false)) {
            val queryIntent = Intent(ctx, FlowQueryService::class.java)
            Log.i(CommHelper.DEBUG_TAG, "开机启动，已开启流量跳点查询服务！")
            Toast.makeText(ctx, "开机启动，已开启流量跳点查询服务！", Toast.LENGTH_SHORT).show()
            ctx.startService(queryIntent)
        }

        // 网络状态改变，开启或停止流量查询服务
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && intent.action == "android.net.conn.CONNECTIVITY_CHANGE") {
            val networkStatus = CommHelper.getConnectivityStatus(ctx)
            val queryIntent = Intent(ctx, FlowQueryService::class.java)
            Log.i(CommHelper.DEBUG_TAG, "手机网络状态已改变:$networkStatus")
            when (networkStatus) {
                CommHelper.TYPE_MOBILE -> {
                    Log.i(CommHelper.DEBUG_TAG, "网络状态已改变为 手机数据，开启流量查询服务")
                    Toast.makeText(ctx, "网络状态已改变为 手机数据，开启流量查询服务", Toast.LENGTH_SHORT).show()
                    ctx.startService(queryIntent)
                }
                else -> {
                    Log.i(CommHelper.DEBUG_TAG, "网络状态已改变为 非手机数据，停止流量查询服务")
                    Toast.makeText(ctx, "网络状态已改变为 非手机数据，停止流量查询服务", Toast.LENGTH_SHORT).show()
                    ctx.stopService(queryIntent)
                }
            }
        }
    }
}