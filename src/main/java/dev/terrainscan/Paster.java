package dev.terrainscan;

import static dev.terrainscan.TerrainScan.fail;
import static dev.terrainscan.TerrainScan.msg;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

final class Paster {
	static final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
	static List<Backup> lastPaste = List.of();

	private Paster() {}

	record Backup(BlockPos origin, StructureTemplate template) {}

	static void tick(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (!jobs.isEmpty() && server != null) server.execute(jobs.poll());
	}

	static int paste(FabricClientCommandSource src, String name) {
		var server = src.getClient().getSingleplayerServer();
		if (server == null) return fail(src, "error.singleplayer_only", "/paste");
		if (!ScanFiles.exists(name)) return fail(src, "error.no_such_scan", name);
		if (!jobs.isEmpty()) return fail(src, "error.busy");

		List<Path> files;
		ScanFiles.Meta meta;
		try {
			meta = ScanFiles.readMeta(name);
			files = ScanFiles.chunkFiles(name);
		} catch (IOException | RuntimeException e) {
			TerrainScan.LOGGER.error("Could not read scan '{}'", name, e);
			return fail(src, "error.read_failed", name, e.getMessage());
		}
		if (files.isEmpty()) return fail(src, "error.empty_scan", name);
		server.execute(() -> server.tickRateManager().setFrozen(true));

		var uuid = src.getPlayer().getUUID();
		List<Backup> backups = Collections.synchronizedList(new ArrayList<>());
		lastPaste = backups;
		int[] done = {0};
		for (Path f : files) {
			int[] xz = ScanFiles.chunkOf(f);
			BlockPos origin = new BlockPos(xz[0] << 4, meta.minY(), xz[1] << 4);
			jobs.add(forPlayer(server, uuid, player -> {
				ServerLevel level = player.level();
				try {
					var t = load(level, f);
					var backup = new StructureTemplate();
					backup.fillFromWorld(level, origin, t.getSize(), true, List.of());
					backup.load(level.holderLookup(Registries.BLOCK), Decorations.filter(backup.save(new CompoundTag())));
					backups.add(new Backup(origin, backup));
					Decorations.clear(level, origin, t.getSize());
					place(level, t, origin);
				} catch (Exception e) {
					TerrainScan.LOGGER.error("Paste of {} failed", f, e);
					player.sendSystemMessage(msg("paste.chunk_failed", f.getFileName(), e));
				}
				if (++done[0] % 20 == 0 || done[0] == files.size())
					player.sendOverlayMessage(msg("paste.progress", name, done[0], files.size()));
			}));
		}
		jobs.add(forPlayer(server, uuid, player -> {
			int top = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, meta.centerX(), meta.centerZ());
			player.teleportTo(meta.centerX() + 0.5, top + 1, meta.centerZ() + 0.5);
		}));
		src.sendFeedback(msg("paste.started", name, files.size()));
		return 1;
	}

	static int undo(FabricClientCommandSource src) {
		var server = src.getClient().getSingleplayerServer();
		if (server == null) return fail(src, "error.singleplayer_only", "/paste undo");
		if (!jobs.isEmpty()) return fail(src, "error.busy");
		if (lastPaste.isEmpty()) return fail(src, "error.nothing_to_undo");
		var uuid = src.getPlayer().getUUID();
		for (Backup b : lastPaste)
			jobs.add(forPlayer(server, uuid, player -> {
				Decorations.clear(player.level(), b.origin, b.template.getSize());
				place(player.level(), b.template, b.origin);
			}));
		jobs.add(forPlayer(server, uuid, player -> player.sendSystemMessage(msg("undo.finished"))));
		lastPaste = List.of();
		src.sendFeedback(msg("undo.started"));
		return 1;
	}

	private static Runnable forPlayer(MinecraftServer server, UUID uuid, Consumer<ServerPlayer> job) {
		return () -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) job.accept(player);
		};
	}

	private static StructureTemplate load(ServerLevel level, Path f) throws IOException {
		var t = new StructureTemplate();
		t.load(level.holderLookup(Registries.BLOCK), NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()));
		return t;
	}

	private static void place(ServerLevel level, StructureTemplate t, BlockPos origin) {
		var settings = new StructurePlaceSettings().setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
		t.placeInWorld(level, origin, origin, settings, RandomSource.create(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
