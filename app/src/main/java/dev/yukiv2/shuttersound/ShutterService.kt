package dev.yukiv2.shuttersound

import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.system.exitProcess

/**
 * Shizuku starts this class with shell privileges. Never expose it as a normal exported service.
 */
class ShutterService : IShutterService.Stub() {
    private val audioSystem = Class.forName("android.media.AudioSystem")

    init {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/media/AudioSystem;")
    }

    override fun getForSystem(): Int {
        val method = audioSystem.getMethod("getForceUse", Int::class.javaPrimitiveType)
        return method.invoke(null, FOR_SYSTEM) as Int
    }

    override fun setForSystem(config: Int) {
        require(config == FORCE_NONE || config == FORCE_SYSTEM_ENFORCED)
        val method = audioSystem.getMethod(
            "setForceUse",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        val result = method.invoke(null, FOR_SYSTEM, config) as Int
        check(result == 0) { "AudioSystem.setForceUse failed: $result" }
    }

    override fun destroy() {
        exitProcess(0)
    }

    companion object {
        const val FOR_SYSTEM = 4
        const val FORCE_NONE = 0
        const val FORCE_SYSTEM_ENFORCED = 11
    }
}
