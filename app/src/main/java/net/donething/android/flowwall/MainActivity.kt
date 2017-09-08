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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Hello.", Snackbar.LENGTH_LONG).setAction("Action", null).show()
        }

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
    }

    override fun onDestroy() {
        Log.i(CommHelper.DEBUG_TAG, "取消注册流量查询的动态广播")
        unregisterReceiver(queryServiceReceiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        this.switchQueryService = menu.findItem(R.id.menuQueryService).actionView.findViewById<Switch>(R.id.swQueryService)
        val switchQueryService = this.switchQueryService ?: return true
        switchQueryService.isChecked = sharedPre?.getBoolean(CommHelper.IS_FLOW_QUERY_SERVICE_RUNNING, false) ?: false
        switchQueryService.setOnCheckedChangeListener {
            btn, isChecked ->
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

    fun startOrStopQueryService(btn: CompoundButton, isStart: Boolean) {
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
    val queryServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == CommHelper.QUERY_SERVICE_ACTION) {
                val status = intent.getBooleanExtra(CommHelper.QUERY_SERVICE_STATUS, false)
                Log.i(CommHelper.DEBUG_TAG, "已接收到流量查询服务状态改变的广播，开始设置switchQueryService状态：$status")
                switchQueryService?.isChecked = status
            }
        }
    }

    var sharedPre: SharedPreferences? = null
    var switchQueryService: Switch? = null
}
