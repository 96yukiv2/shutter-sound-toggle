package dev.yukiv2.shuttersound

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var service: IShutterService? = null
    private var shizukuRunning by mutableStateOf(false)
    private var authorized by mutableStateOf(false)
    private var silent by mutableStateOf<Boolean?>(null)
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShutterService::class.java.name))
            .daemon(false)
            .processNameSuffix("audio")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            busy = false
            if (binder == null || !binder.pingBinder()) {
                error = "音声サービスへの接続に失敗しました"
                return
            }
            service = IShutterService.Stub.asInterface(binder)
            readState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            silent = null
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            authorized = grantResult == PackageManager.PERMISSION_GRANTED
            if (authorized) connect() else error = "Shizukuの権限が必要です"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        setContent {
            MaterialTheme {
                Scaffold { inset ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(inset).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("シャッター音", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                !shizukuRunning -> "Shizukuが起動していません"
                                !authorized -> "Shizukuの権限が必要です"
                                silent == true -> "強制シャッター音：解除中"
                                silent == false -> "強制シャッター音：有効"
                                else -> "状態を確認してください"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(24.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("強制シャッター音を解除", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                Switch(
                                    checked = silent == true,
                                    enabled = authorized && service != null && silent != null && !busy,
                                    onCheckedChange = { enabled -> changeState(enabled) },
                                )
                                Text(
                                    "解除中も通常モードでは音が鳴ります。無音にするにはマナーモードにしてください。",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (!shizukuRunning) {
                            Text("Shizukuをインストール・起動してから再確認してください。")
                            Button(onClick = ::refresh) { Text("再確認") }
                        } else if (!authorized) {
                            Button(onClick = ::requestPermission) { Text("権限を許可") }
                        } else {
                            TextButton(onClick = ::refresh, enabled = !busy) { Text("状態を再取得") }
                        }
                        error?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        error = null
        try {
            shizukuRunning = Shizuku.pingBinder() && !Shizuku.isPreV11()
            authorized = shizukuRunning &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (authorized) connect() else {
                service = null
                silent = null
            }
        } catch (e: Exception) {
            shizukuRunning = false
            authorized = false
            silent = null
            error = e.message
        }
    }

    private fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                error = "Shizuku側で権限を許可してください"
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            error = e.message ?: "権限リクエストに失敗しました"
        }
    }

    private fun connect() {
        if (service?.asBinder()?.pingBinder() == true) {
            readState()
            return
        }
        busy = true
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Exception) {
            busy = false
            error = e.message ?: "Shizukuへの接続に失敗しました"
        }
    }

    private fun readState() {
        try {
            val force = service?.forSystem ?: return
            silent = force == ShutterService.FORCE_NONE
            error = if (force != ShutterService.FORCE_NONE &&
                force != ShutterService.FORCE_SYSTEM_ENFORCED
            ) "未対応の音声ポリシー: $force" else null
        } catch (e: Exception) {
            silent = null
            error = e.message ?: "状態の取得に失敗しました"
        }
    }

    private fun changeState(enabled: Boolean) {
        val remote = service ?: return
        busy = true
        try {
            val default = if (MediaActionSound.mustPlayShutterSound()) {
                ShutterService.FORCE_SYSTEM_ENFORCED
            } else {
                ShutterService.FORCE_NONE
            }
            remote.forSystem = if (enabled) ShutterService.FORCE_NONE else default
            readState()
        } catch (e: Exception) {
            error = e.message ?: "切り替えに失敗しました"
            readState()
        } finally {
            busy = false
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE = 100
    }
}
