package com.hongtian.seefox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class FeaturesFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_features, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        val ctx = requireContext()

        val switchLayout = v.findViewById<View>(R.id.colorBlindSwitch)
        val switchText = v.findViewById<TextView>(R.id.switchText)

        // 读取当前状态
        val currentMode = Settings.Secure.getInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", 0)
        if (currentMode == 1) {
            switchText?.text = "开启"
        } else {
            switchText?.text = "关闭"
        }

        switchLayout?.setOnClickListener {
            val isOn = Settings.Secure.getInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", 0) == 1
            try {
                if (isOn) {
                    Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", 0)
                    switchText?.text = "关闭"
                    Toast.makeText(ctx, "全色盲模式已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    Settings.Secure.putInt(ctx.contentResolver, "accessibility_display_daltonizer_enabled", 1)
                    Settings.Secure.putString(ctx.contentResolver, "accessibility_display_daltonizer_correction", "0")
                    switchText?.text = "开启"
                    Toast.makeText(ctx, "全色盲模式已开启", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showPermissionDialog()
            }
        }
    }

    private fun showPermissionDialog() {
        val ctx = requireContext()
        val cmd = "adb shell pm grant com.hongtian.seefox android.permission.WRITE_SECURE_SETTINGS"
        val editText = EditText(ctx).apply {
            setText(cmd)
            isFocusable = false
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(ctx)
            .setTitle("需要授权一次")
            .setMessage("全色盲模式需要 WRITE_SECURE_SETTINGS 权限。\n\n用电脑执行以下命令一次即可，以后永久有效：")
            .setView(editText)
            .setPositiveButton("复制命令") { _, _ ->
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("cmd", cmd))
                Toast.makeText(ctx, "命令已复制，到电脑执行", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("知道了", null)
            .show()
    }
}
