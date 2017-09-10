package net.donething.android.flowwall

import android.content.*
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import android.widget.Switch
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.hide()
        /*
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Hello.", Snackbar.LENGTH_LONG).setAction("Action", null).show()
        }
        */

        CommHelper.runCmdAsSu("exit\n")

        sharedPre = PreferenceManager.getDefaultSharedPreferences(this)

        // 当FlowQueryService端停止手动停止时，需要发送广播通知MainActivity更新UI
        Log.i(CommHelper.DEBUG_TAG, "注册流量查询的动态广播")
        val filter = IntentFilter()
        filter.addAction(CommHelper.QUERY_SERVICE_ACTION)
        registerReceiver(queryServiceReceiver, filter)
    }

    override fun onStart() {
        super.onStart()
        Thread(queryTask).start()
    }

    override fun onDestroy() {
        Log.i(CommHelper.DEBUG_TAG, "取消注册流量查询的动态广播")
        unregisterReceiver(queryServiceReceiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        this.switchQueryService = menu.findItem(R.id.menuQueryService).actionView.findViewById(R.id.swQueryService)
        val switchQueryService = this.switchQueryService ?: return true
        switchQueryService.isChecked = sharedPre?.getBoolean(CommHelper.IS_FLOW_QUERY_SERVICE_RUNNING, false) ?: false
        switchQueryService.setOnCheckedChangeListener { btn, isChecked ->
            startOrStopQueryService(btn, isChecked)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val settingsIntent = Intent(this, SettingsActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                settingsIntent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsActivity.GeneralPreferenceFragment::class.java.name)
                settingsIntent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true)
                startActivity(settingsIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startOrStopQueryService(btn: CompoundButton, isStart: Boolean) {
        val queryIntent = Intent(this, FlowQueryService::class.java)
        if (isStart) {
            startService(queryIntent)
            Snackbar.make(btn, "已开启 流量查询服务！", Snackbar.LENGTH_SHORT).setAction("Action", null).show()
        } else {
            stopService(queryIntent)
            Snackbar.make(btn, "已关闭 流量查询服务！", Snackbar.LENGTH_SHORT).setAction("Action", null).show()
        }
    }

    // 在主界面可见时，当FlowQueryService开启或停止时，需要更改swQueryService状态
    private val queryServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == CommHelper.QUERY_SERVICE_ACTION) {
                val status = intent.getBooleanExtra(CommHelper.QUERY_SERVICE_STATUS, false)
                Log.i(CommHelper.DEBUG_TAG, "已接收到流量查询服务状态改变的广播，开始设置switchQueryService状态：$status")
                switchQueryService?.isChecked = status
            }
        }
    }

    // 查询流量
    private val queryTask = Runnable {
        val phoneNum = sharedPre?.getString(CommHelper.PHONE_NUM, "") ?: ""
        val queryJSONResult = CommHelper.queryFlowValue(phoneNum)
        runOnUiThread {
            if (phoneNum == "") {
                flow_query_text.text = "还没有填写手机号"
                return@runOnUiThread
            }

            if (queryJSONResult.success) {
                flow_query_text.text = "$phoneNum 流量情况：\n" + JSONObject(queryJSONResult.msg)
                        .getJSONObject("data").getJSONArray("result").toString(4)
            } else {
                flow_query_text.text = "$phoneNum 流量查询出错：\n" + JSONObject(queryJSONResult.msg).toString(4)
            }
        }
    }

    private var sharedPre: SharedPreferences? = null
    private var switchQueryService: Switch? = null
}
