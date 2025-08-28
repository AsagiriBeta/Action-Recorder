package asagiribeta.actionRecorder.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;

public final class ActionRecorderClientCommandRegistrar {
    private ActionRecorderClientCommandRegistrar() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(ActionRecorderClientCommandRegistrar::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        dispatcher.register(
            ClientCommandManager.literal("ar")
                .then(ClientCommandManager.literal("start").executes(ctx -> {
                    boolean ok = Recorder.INSTANCE.start();
                    ActionRecorderClientCommands.INSTANCE.feedback(ok ? "录制开始" : "录制已在进行中");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> {
                    boolean ok = Recorder.INSTANCE.stop();
                    ActionRecorderClientCommands.INSTANCE.feedback(ok ? "录制已停止" : "录制未在进行");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    boolean r = Recorder.INSTANCE.isRecording();
                    ActionRecorderClientCommands.INSTANCE.feedback(r ? "状态: 录制中" : "状态: 未录制");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("set")
                    .then(ClientCommandManager.argument("key", StringArgumentType.string())
                        .then(ClientCommandManager.argument("value", StringArgumentType.string()).executes(ctx -> setAny(ctx.getSource(), StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "value"))))
                        .then(ClientCommandManager.argument("value_i", IntegerArgumentType.integer()).executes(ctx -> setAny(ctx.getSource(), StringArgumentType.getString(ctx, "key"), IntegerArgumentType.getInteger(ctx, "value_i"))))
                        .then(ClientCommandManager.argument("value_l", LongArgumentType.longArg()).executes(ctx -> setAny(ctx.getSource(), StringArgumentType.getString(ctx, "key"), LongArgumentType.getLong(ctx, "value_l"))))
                        .then(ClientCommandManager.argument("value_d", DoubleArgumentType.doubleArg()).executes(ctx -> setAny(ctx.getSource(), StringArgumentType.getString(ctx, "key"), DoubleArgumentType.getDouble(ctx, "value_d"))))
                    )
                )
        );
    }

    private static int setAny(FabricClientCommandSource src, String key, Object value) {
        String res = ActionRecorderClientCommands.INSTANCE.applyConfig(key, value);
        ActionRecorderClientCommands.INSTANCE.feedback(res);
        return Command.SINGLE_SUCCESS;
    }
}

