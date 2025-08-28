package asagiribeta.actionRecorder.mixin.client;

import asagiribeta.actionRecorder.client.Recorder;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void actionrecorder$doAttack(CallbackInfoReturnable<Boolean> cir) {
        Recorder.INSTANCE.onDoAttackTriggered();
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void actionrecorder$doItemUse(CallbackInfo ci) {
        Recorder.INSTANCE.onDoItemUseTriggered();
    }
}

