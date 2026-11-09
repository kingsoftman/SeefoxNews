package com.hongtian.seefox

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class MeFragment : Fragment() {
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = i.inflate(R.layout.fragment_me, c, false)
        prefs = requireContext().getSharedPreferences("seefox_prefs", Activity.MODE_PRIVATE)

        // 点击头像换头像
        v.findViewById<View>(R.id.avatarLayout)?.setOnClickListener {
            Toast.makeText(context, "换头像功能开发中...", Toast.LENGTH_SHORT).show()
        }

        // 点击昵称改昵称
        v.findViewById<TextView>(R.id.nicknameText)?.setOnClickListener {
            showEditNicknameDialog()
        }

        // 深浅色完全跟随系统，无手动开关

        return v
    }

    private fun showEditNicknameDialog() {
        val input = EditText(context)
        input.setText(prefs.getString("nickname", "小书包"))
        AlertDialog.Builder(requireContext())
            .setTitle("修改昵称")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit().putString("nickname", newName).apply()
                    view?.findViewById<TextView>(R.id.nicknameText)?.text = newName
                    Toast.makeText(context, "昵称修改成功", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
