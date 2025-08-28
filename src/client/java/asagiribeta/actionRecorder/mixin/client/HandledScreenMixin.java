package asagiribeta.actionRecorder.mixin.client;

import asagiribeta.actionRecorder.client.Recorder;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"))
    private void actionrecorder$onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        HandledScreen<?> self = (HandledScreen<?>)(Object)this;
        String title = self.getTitle().getString();
        int idx = slot != null ? slot.getIndex() : slotId;
        Recorder.INSTANCE.log("container_click", Map.of(
                "title", title,
                "slot", idx,
                "button", button,
                "action", actionType.name()
        ));
    }
}

