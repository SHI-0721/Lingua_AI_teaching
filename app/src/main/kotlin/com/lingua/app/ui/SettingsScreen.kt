package com.lingua.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lingua.app.data.Settings
@Composable fun SettingsScreen(settings: Settings,vm: LearningViewModel) = Page {
    Text("显示模式",style=MaterialTheme.typography.titleLarge)
    listOf("system" to "跟随系统","light" to "浅色","dark" to "深色").forEach { (value,label) -> OutlinedButton(onClick={ vm.setTheme(value) },modifier=Modifier.fillMaxWidth()) { Text((if(settings.theme==value) "✓ " else "")+label) } }
    Text("每日目标：${settings.dailyGoal} 个")
    listOf(5,10,20).forEach { goal -> OutlinedButton(onClick={ vm.setGoal(goal) }) { Text("每天 $goal 个") } }
    val syncing by vm.syncing.collectAsState()
    val message by vm.syncMessage.collectAsState()
    Text("云端词库",style=MaterialTheme.typography.titleLarge)
    Text("已内置官方初级词库，可离线搜索和学习。新云端词库仍未发布，当前更新只读取公开内容。学习记录仅保存在手机。")
    Button(onClick={ vm.syncContent() },enabled=!syncing,modifier=Modifier.fillMaxWidth()) { Text(if(syncing) "正在更新…" else "更新词库") }
    if(message.isNotBlank()) Text(message)
    Text("只下载公开词库，不上传学习记录。断网仍可学习；暂不自动移除下架内容。")
}
