package net.donething.android.flowwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || CommHelper.getConnectivityStatus(ctx) == ConnectivityManager.TYPE_MOBILE) {
                val queryIntent = Intent(ctx, FlowQueryService::class.java)
                Log.i(CommHelper.DEBUG_TAG, "开机启动，已开启流量跳点查询服务！")
                Toast.makeText(ctx, "开机启动，已开启流量跳点查询服务！", Toast.LENGTH_SHORT).show()
                ctx.startService(queryIntent)
            }
        }

        // 网络状态改变，开启或停止流量查询服务
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && intent.action == "android.net.conn.CONNECTIVITY_CHANGE") {
            val queryIntent = Intent(ctx, FlowQueryService::class.java)
            Log.i(CommHelper.DEBUG_TAG, "手机网络状态已改变为:" + CommHelper.getConnectivityStatus(ctx, true))
            if (CommHelper.getConnectivityStatus(ctx) == ConnectivityManager.TYPE_MOBILE) {
                Log.i(CommHelper.DEBUG_TAG, "已改变为移动网络，开启流量查询服务")
                Toast.makeText(ctx, "已改变为移动网络，开启流量查询服务", Toast.LENGTH_SHORT).show()
                ctx.startService(queryIntent)
            } else {
                if (sharedPre.getBoolean(CommHelper.IS_FLOW_QUERY_SERVICE_RUNNING, false)) {
                    Log.i(CommHelper.DEBUG_TAG, "已改变为非移动网络，停止流量查询服务")
                    Toast.makeText(ctx, "已改变为非移动网络，停止流量查询服务", Toast.LENGTH_SHORT).show()
                    ctx.stopService(queryIntent)
                }
            }
        }
    }
}