package com.situstechnologies.OXray.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher

/**
 * VPN 权限管理辅助类
 * 复用 MainActivity 的逻辑
 */
object VpnPermissionHelper {

    /**
     * 检查并请求 VPN 权限
     * @return true 如果需要请求权限，false 如果已有权限
     */
    fun checkAndRequestPermission(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>
    ): Boolean {
        val intent = VpnService.prepare(activity)
        return if (intent != null) {
            launcher.launch(intent)
            true
        } else {
            false
        }
    }
}