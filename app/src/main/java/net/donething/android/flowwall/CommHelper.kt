package net.donething.android.flowwall

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException
import java.net.URL


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

        /**
         * 获取网络连接状态
         * @param context Context对象
         * @param statusAsString 返回状态值（Int，默认）还是状态字符串（String）
         */
        fun getConnectivityStatus(context: Context, statusAsString: Boolean = false): Any {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            if (statusAsString) {
                activeNetwork ?: return "无网络连接"
                return activeNetwork.typeName
            }
            activeNetwork ?: return -1
            return activeNetwork.type
        }

        /**
         * 获取流量信息
         * @param phoneNum 带查询的手机号
         * @return 返回流量结果数据，其中code意思：10：获取数据成功；20：获取的流量值为null；21从获取的json字符串中解析不到有用数据；30：程序运行出现异常
         */
        fun queryFlowValue(phoneNum: String): JSONResult {
            try {
                val queryResult = URL(FLOW_QUERY_URL + phoneNum).readText()
                val flowJson = JSONObject(queryResult)
                val flowData = flowJson.getJSONObject("data")
                if (flowJson["status"] == "success" && flowData["code"] == "10000") {
                    val flowResult = flowData.getJSONArray("result")
                    val currentUsedFlow = (flowResult[1] as JSONObject).getString("used").toDoubleOrNull()
                    currentUsedFlow ?: return JSONResult(false, 20, queryResult)
                    return JSONResult(true, 10, queryResult, currentUsedFlow)
                } else {
                    return JSONResult(false, 21, queryResult)
                }
            } catch (ex: Exception) {
                return JSONResult(false, 30, ex.toString())
            }
        }

        val DEBUG_TAG = "[Flow Wall]"

        val CMD_DISABLE_DATA = "svc data disable\n"     // shell关闭移动数据连接的命令

        // SharedPreference key name
        val IS_FLOW_QUERY_SERVICE_RUNNING = "is_flow_query_service_running"     // 是否已经启动了流量查询服务
        val PHONE_NUM = "phone_num"                     // 带查询的手机号
        val IS_BOOT_START = "is_boot_start"             // 是否开机启动查询服务
        val QUERY_FREQUENCY = "query_frequency"         // 查询频率
        val FLOW_INTERVAL = "flow_interval"             // 断网流量差
        val IS_AUTO_DISCONNECT_DATA = "is_auto_disconnect_data"     // 超过流量差时是否自动断网
        val IS_CONNECT_MOBILE_START = "is_connect_mobile_start"     // 当连接到移动网络时主动运行服务（接收网络状态改变的广播 <=Android6.0.1）
        val APP_VERSION = "app_version"                 // 应用版本
        val ANDROID_ID = "android_id"                   // Android ID


        // MainActivity and FlowQueryService Broadcast
        val QUERY_SERVICE_ACTION = "net.donething.android.flowwall.query_service_ACTION"
        val QUERY_SERVICE_STATUS = "query_service_status"

        // Query flaw data
        private val FLOW_QUERY_URL = "http://58.250.151.66/wowap-interface/flowstore/flowstoreActionQuery?mobile="
    }
}