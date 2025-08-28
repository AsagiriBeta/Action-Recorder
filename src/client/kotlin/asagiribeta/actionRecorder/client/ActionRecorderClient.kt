package asagiribeta.actionRecorder.client

import net.fabricmc.api.ClientModInitializer

class ActionRecorderClient : ClientModInitializer {

    override fun onInitializeClient() {
        // 初始化录制器
        Recorder.init()
        // 注册客户端指令（Java 实现，避免泛型问题）
        ActionRecorderClientCommandRegistrar.register()
    }
}
