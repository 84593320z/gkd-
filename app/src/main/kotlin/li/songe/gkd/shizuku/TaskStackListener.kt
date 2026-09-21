package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Parcel
import android.view.Display
import li.songe.gkd.a11y.ActivityScene
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.a11y.updateTopActivity
import li.songe.gkd.util.AndroidTarget

object FixedTaskStackListener : ITaskStackListener.Stub() {

    // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = try {
        super.onTransact(code, data, reply, flags)
    } catch (_: Throwable) {
        true
    }

    override fun onTaskStackChanged(): Unit = synchronized(topActivityFlow) {
        val cpn = shizukuContextFlow.value.topCpn() ?: return
        if (lastFront.first > 0 && lastFront.second == cpn && System.currentTimeMillis() - lastFront.first > 200) {
            lastFront = defaultFront
            return
        }
        updateTopActivity(
            appId = cpn.packageName,
            activityId = cpn.className,
            scene = ActivityScene.TaskStack,
        )
    }

    private val defaultFront = 0L to ComponentName("", "")
    private var lastFront = defaultFront
    private fun onTaskMovedToFrontCompat(
        cpn: ComponentName? = null
    ): Unit = synchronized(topActivityFlow) {
        val cpn = cpn ?: shizukuContextFlow.value.topCpn() ?: return
        lastFront = System.currentTimeMillis() to cpn
        updateTopActivity(
            appId = cpn.packageName,
            activityId = cpn.className,
            scene = ActivityScene.TaskStack,
        )
    }

    override fun onTaskMovedToFront(taskId: Int) {
        val taskInfo = shizukuContextFlow.value.getTasks().firstOrNull() ?: return
        @Suppress("DEPRECATION")
        if (taskInfo.id != taskId) {
            return
        }
        onTaskMovedToFrontCompat(taskInfo.topActivity)
    }

    override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
        if (AndroidTarget.Q && taskInfo.casted.displayId != Display.DEFAULT_DISPLAY) {
            return
        }
        onTaskMovedToFrontCompat(taskInfo.topActivity)
    }
}
