package net.donething.android.flowwall

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.net.URL
import java.util.*


class FlowQueryService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        sharedPre = PreferenceManager.getDefaultSharedPreferences(this)
        noManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        noBuilderCorrect = createNoBuilder()
        noBuilderWarn = createNoBuilder()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        sharedPre?.edit()?.putBoolean(CommHelper.IS_FLOW_QUERY_SERVICE_RUNNING, true)?.apply()    // 服务已开启
        phoneNum = sharedPre?.getString(CommHelper.PHONE_NUM, "") ?: ""
        queryFrequency = sharedPre?.getString(CommHelper.QUERY_FREQUENCY, queryFrequency.toString())?.toIntOrNull() ?: queryFrequency
        flowInterval = sharedPre?.getString(CommHelper.FLOW_INTERVAL, flowInterval.toString())?.toFloatOrNull() ?: flowInterval
        isAutoDisconnectData = sharedPre?.getBoolean(CommHelper.IS_AUTO_DISCONNECT_DATA, isAutoDisconnectData) ?: isAutoDisconnectData

        try {
            if (phoneNum.isNotEmpty()) {
                timer.schedule(queryTask, 0, queryFrequency * 60 * 1000L)
                makeNotification("正在查询流量……", "请稍等3秒会自动更新通知……")
            } else {
                makeNotification("没有填写手机号", "请先进入设置中填写手机号", true)
                stopSelf()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        queryTask.cancel()  // 取消定时查询流量的任务
        noManager?.cancel(NO_CORRECT_ID) // 取消通知
        sharedPre?.edit()?.putBoolean(CommHelper.IS_FLOW_QUERY_SERVICE_RUNNING, true)?.apply()    // 服务已开启

        // 保存流量查询服务状态为停止，以供MainActivity创建时显示swQueryService的状态
        sharedPre?.edit()?.putBoolean(CommHelper.IS_FLOW_QUERY_SERVICE_RUNNING, false)?.apply()
        // 发送流量查询服务停止的广播，以供MainActivity未销毁时实时显示swQueryService的状态
        Log.i(CommHelper.DEBUG_TAG, "流量查询服务已停止，发送通知广播")
        val serviceStopIntent = Intent(CommHelper.QUERY_SERVICE_ACTION)
        serviceStopIntent.putExtra(CommHelper.IS_QUERY_SERVICE_STOP, true)
        sendBroadcast(serviceStopIntent)

        super.onDestroy()
    }

    // 定时器
    val queryTask = object : TimerTask() {
        override fun run() {
            queryFlow()
        }
    }

    /**
     * 实现查询、监控流量
     */
    private fun queryFlow() {
        // 非移动网络时，无需查询流量
        if (!(connManager?.activeNetworkInfo?.isConnected ?: false && connManager?.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE)) {
            makeNotification("暂停流量查询", "当前为非移动网络，无需查询流量")
            return
        }
        try {
            val queryResult = URL(FLOW_QUERY_URL + phoneNum).readText()
            val flowJson: Map<String, Any> = gson.fromJson(queryResult, object : TypeToken<Map<String, Any>>() {}.type)
            val flowData = flowJson["data"] as Map<String, Any>
            if (flowJson["status"] == "success" && flowData["code"] == "10000") {
                val flowResult = flowData["result"] as ArrayList<Map<String, String>>
                val currentUsedFlow = flowResult[1]["used"]?.toDoubleOrNull()
                if (currentUsedFlow == null) {
                    makeNotification("流量查询出错", queryResult, true)
                    stopSelf()
                    return
                }
                // 负数表示为首次查询
                if (lastUsedFlow < 0) {
                    lastUsedFlow = currentUsedFlow
                    makeNotification("此次为首次查询", "将于下次(${queryFrequency}分钟)后开始比对，共用流量${lastUsedFlow}MB")
                    return
                }
                // 比对两次流量差，判断流量跳点是否正常
                val flowInterval = currentUsedFlow - lastUsedFlow
                if (flowInterval >= this.flowInterval) {
                    makeNotification("流量跳点过高", "%d分钟内跳点%.2fMB，已关闭数据连接".format(queryFrequency, flowInterval))
                    if (isAutoDisconnectData) CommHelper.runCmdAsSu(CommHelper.CMD_DISABLE_DATA)    // 可选断网
                    stopSelf()
                } else {
                    makeNotification("流量跳点正常", "跳点%.2fMB，共用流量%.2fMB".format(flowInterval, currentUsedFlow))
                }
                lastUsedFlow = currentUsedFlow
            } else {
                makeNotification("流量查询出错", flowData["message"].toString(), true)
                stopSelf()
            }
        } catch (ex: Exception) {
            makeNotification("APP运行出现异常", ex.toString(), true)
            stopSelf()
        }
    }

    /**
     * 创建通知Builder对象
     */
    private fun createNoBuilder(): Notification.Builder {
        val mainIntent = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
        val builder = Notification.Builder(this)
                .setSmallIcon(R.drawable.no_flow)
                .setShowWhen(true)
                .setContentIntent(mainIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
        return builder
    }

    /**
     * 生成通知
     * @param title 通知标题
     * @param text 通知内容
     * @param warn 默认false表示提示通知，true表示警告内通知。警告通知将发出声音和闪光
     */
    private fun makeNotification(title: String, text: String, warn: Boolean = false) {
        val noBuilder = (if (warn) noBuilderWarn else noBuilderCorrect) ?: createNoBuilder()
        noBuilder.setWhen(System.currentTimeMillis())
                .setContentTitle(title)
                .setContentText(text)
        val no = noBuilder.build()
        if (warn) {
            no.flags = no.flags or Notification.FLAG_AUTO_CANCEL
            no.priority = Notification.PRIORITY_MAX
            no.defaults = no.defaults or Notification.DEFAULT_SOUND or Notification.DEFAULT_LIGHTS
        } else {
            no.flags = no.flags or Notification.FLAG_NO_CLEAR
        }
        noManager?.notify(if (warn) NO_WARN_ID else NO_CORRECT_ID, no)
    }

    /* NetworkCallback（>= Android N）获取网络状态（鸡肋）
    class ConnectionStateMonitor(context: Context) : ConnectivityManager.NetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

        /**
         * 注册网络改变广播
         */
        fun enable() {
            connectivityManager.registerNetworkCallback(networkRequest, this)
        }

        /**
         * 取消注册网络改变广播
         */
        fun disable() {
            connectivityManager.unregisterNetworkCallback(this)
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (connectivityManager.activeNetworkInfo.isConnected) {
                Log.i(CommHelper.DEBUG_TAG + "连接网络：", connectivityManager.activeNetworkInfo.type.toString())

            }
        }

        override fun onLost(network: Network?) {
            super.onLost(network)
            Log.i(CommHelper.DEBUG_TAG + "断开网络：", network.toString())
        }
    }
    */

    var sharedPre: SharedPreferences? = null
    var connManager: ConnectivityManager? = null

    val timer = Timer()     // 定时器
    val FLOW_QUERY_URL = """http://58.250.151.66/wowap-interface/flowstore/flowstoreActionQuery?mobile="""
    var phoneNum = ""
    val gson = GsonBuilder().setPrettyPrinting().create()

    var noManager: NotificationManager? = null      // 通知管理
    var noBuilderCorrect: Notification.Builder? = null     // 正常运行时的notification的Builder，用于更新notification显示文本
    var noBuilderWarn: Notification.Builder? = null  // APP运行异常或流量跳点超过阀值时的notification的Builder，用于更新notification显示文本
    val NO_CORRECT_ID = 8844  // 正常运行时的notification的ID
    val NO_WARN_ID = 8848  // 发生异常时的notification的ID

    var lastUsedFlow = -1.0  // 记录上次查询到的已使用流量（负数表示为首次查询）
    var queryFrequency = 3   // 流量查询间隔（分钟）
    var flowInterval = 3.0F   // 流量跳点阀值（MB）
    var isAutoDisconnectData = false    // 自动断网
}
