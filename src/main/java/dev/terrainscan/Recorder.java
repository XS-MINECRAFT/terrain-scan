package dev.terrainscan;

import static dev.terrainscan.TerrainScan.fail;
import static dev.terrainscan.TerrainScan.msg;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

final class Recorder {
	static final int SCAN_DELAY_TICKS = 40;
	static final int SCANS_PER_TICK = 2;
	static final int DRAW_RADIUS_CHUNKS = 8;

	static Recording active;

	private Recorder() {}

	record Recording(String name, Path dir, ResourceKey<Level> dim, int radius, int minY, int maxY,
			Map<Long, Long> pending, Set<Long> done) {
		boolean nearPlayer(int cx, int cz) {
			var player = Minecraft.getInstance().player;
			return player != null && Math.abs(cx - (player.getBlockX() >> 4)) <= radius && Math.abs(cz - (player.getBlockZ() >> 4)) <= radius;
		}
	}

	static int start(FabricClientCommandSource src, String name, int radius, Integer minY, Integer maxY) {
		if (!ScanFiles.validName(name)) return fail(src, "error.invalid_name");
		if (active != null) return fail(src, "error.already_recording", active.name);
		var level = src.getLevel();
		int lo = Math.max(level.getMinY(), minY == null ? level.getMinY() : Math.min(minY, maxY));
		int hi = Math.min(level.getMaxY(), maxY == null ? level.getMaxY() : Math.max(minY, maxY));
		if (lo > hi) return fail(src, "error.y_outside_world");

		Path dir;
		try {
			dir = ScanFiles.create(name, src.getPlayer().blockPosition(), lo);
		} catch (IOException e) {
			TerrainScan.LOGGER.error("Could not create scan folder for '{}'", name, e);
			return fail(src, "error.create_failed", ScanFiles.dir(name), e.getMessage());
		}
		active = new Recording(name, dir, level.dimension(), radius, lo, hi, new HashMap<>(), new HashSet<>());
		src.sendFeedback(msg("scan.started", name, radius, lo, hi));
		return 1;
	}

	static int stop(FabricClientCommandSource src) {
		if (active == null) return fail(src, "error.not_recording");
		src.sendFeedback(msg("scan.stopped", active.name, active.done.size(), active.dir));
		active = null;
		return 1;
	}

	static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		ChunkPos p = chunk.getPos();
		if (active != null && level.dimension() == active.dim && active.nearPlayer(p.x(), p.z()))
			active.pending.put(p.pack(), TerrainScan.tick + SCAN_DELAY_TICKS);
	}

	static void tick(Minecraft mc) {
		var rec = active;
		if (rec == null || mc.level == null || mc.player == null) return;
		var level = mc.level;
		long now = TerrainScan.tick;
		if (level.dimension() == rec.dim) {
			int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
			for (int cx = pcx - rec.radius; cx <= pcx + rec.radius; cx++)
				for (int cz = pcz - rec.radius; cz <= pcz + rec.radius; cz++) {
					long key = ChunkPos.pack(cx, cz);
					if (!rec.done.contains(key) && level.hasChunk(cx, cz)) rec.pending.putIfAbsent(key, now);
				}
			int budget = SCANS_PER_TICK;
			var it = rec.pending.entrySet().iterator();
			while (budget > 0 && it.hasNext()) {
				var e = it.next();
				if (e.getValue() > now) continue;
				it.remove();
				int cx = ChunkPos.getX(e.getKey()), cz = ChunkPos.getZ(e.getKey());
				if (!level.hasChunk(cx, cz)) continue;
				try {
					scanChunk(rec, level, cx, cz);
					rec.done.add(e.getKey());
				} catch (Exception ex) {
					TerrainScan.LOGGER.error("Scan of chunk {},{} failed", cx, cz, ex);
					mc.player.sendSystemMessage(msg("scan.chunk_failed", cx, cz, ex));
				}
				budget--;
			}
			drawChunks(rec, mc);
		}
		if (now % 10 == 0)
			mc.player.sendOverlayMessage(msg("scan.progress", rec.name, rec.done.size()));
	}

	private static void scanChunk(Recording rec, Level level, int cx, int cz) throws IOException {
		var t = new StructureTemplate();
		t.fillFromWorld(level, new BlockPos(cx << 4, rec.minY, cz << 4), new Vec3i(16, rec.maxY - rec.minY + 1, 16), true, List.of());
		CompoundTag tag = Decorations.filter(t.save(new CompoundTag()));
		NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(tag), ScanFiles.chunkFile(rec.dir, cx, cz));
	}

	private static void drawChunks(Recording rec, Minecraft mc) {
		var p = mc.player.blockPosition();
		double y0 = Math.max(rec.minY, p.getY() - 24), y1 = Math.min(rec.maxY + 1, p.getY() + 24);
		int pcx = p.getX() >> 4, pcz = p.getZ() >> 4, r = Math.min(rec.radius, DRAW_RADIUS_CHUNKS);
		for (int cx = pcx - r; cx <= pcx + r; cx++)
			for (int cz = pcz - r; cz <= pcz + r; cz++) {
				int color = rec.done.contains(ChunkPos.pack(cx, cz)) ? 0xFF33DD33 : 0xFFDD3333;
				Gizmos.cuboid(new AABB(cx << 4, y0, cz << 4, (cx << 4) + 16, y1, (cz << 4) + 16).deflate(0.05), GizmoStyle.stroke(color));
			}
		Gizmos.cuboid(new AABB((pcx - rec.radius) << 4, y0, (pcz - rec.radius) << 4, (pcx + rec.radius + 1) << 4, y1, (pcz + rec.radius + 1) << 4),
			GizmoStyle.stroke(0xFFFFDD00, 4f));
	}
}
