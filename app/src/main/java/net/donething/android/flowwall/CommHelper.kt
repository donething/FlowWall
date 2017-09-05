package net.donething.android.flowwall

import android.content.Context
import android.net.ConnectivityManager
import java.io.DataOutputStream
import java.io.IOException

/**
 * Created by donet on 17-9-4.
 */
class CommHelper {
    companion object {
        /**
         * 以ROOT权限运行cmd命令
         * @param cmd cmd命令
         */
        fun runCmdAsSu(cmd: String) {
            try {
                val su = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(su.outputStream)

                outputStream.writeBytes(cmd)
                outputStream.flush()

                outputStream.writeBytes("exit\n")
                outputStream.flush()
                try {
                    su.waitFor()
                } catch (ex: InterruptedException) {
                    ex.printStackTrace()
                }

                outputStream.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }

        fun getConnectivityStatus(context: Context): Int {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            activeNetwork ?: return TYPE_NOT_CONNECTED
            if (activeNetwork.type == ConnectivityManager.TYPE_WIFI) return TYPE_WIFI
            if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE) return TYPE_MOBILE
            return -1
        }

        val DEBUG_TAG = "[Flow Wall]"

        var TYPE_NOT_CONNECTED = 0
        var TYPE_WIFI = 1
        var TYPE_MOBILE = 2

        // SharedPreference key name
        val HAD_START_QUERY = "had_start_query"     // 是否已经启动了流量查询服务
        val PHONE_NUM = "phone_num"         // 带查询的手机号
        val IS_BOOT_START = "is_boot_start"     // 是否开机启动查询服务

        // MainActivity and FlowQueryService Broadcast
        val QUERY_SERVICE_ACTION = "net.donething.android.flowwall.query_service_ACTION"
        val IS_QUERY_SERVICE_STOP = "is_query_service_stop"
    }
}