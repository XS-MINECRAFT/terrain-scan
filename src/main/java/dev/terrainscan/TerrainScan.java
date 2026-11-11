package dev.terrainscan;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainScan implements ClientModInitializer {
	public static final String MOD_ID = "terrainscan";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	static long tick;

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> ScanCommands.register(dispatcher));
		ClientChunkEvents.CHUNK_LOAD.register(Recorder::onChunkLoad);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!server.isDedicatedServer()) server.tickRateManager().setFrozen(true);
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			tick++;
			Recorder.tick(mc);
			Paster.tick(mc);
		});
	}

	static MutableComponent msg(String key, Object... args) {
		return Component.translatableEscape(MOD_ID + "." + key, args);
	}

	static int fail(FabricClientCommandSource src, String key, Object... args) {
		src.sendError(msg(key, args));
		return 0;
	}
}
