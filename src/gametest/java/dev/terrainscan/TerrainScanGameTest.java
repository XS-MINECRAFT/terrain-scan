package dev.terrainscan;

import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.SignTextSlot;

public class TerrainScanGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		BlockPos base, center;
		int y;

		try (var server = ctx.worldBuilder().createServer(); var conn = server.connect()) {
			conn.waitForChunksRender();
			check(ctx.computeOnClient(mc -> net.minecraft.locale.Language.getInstance().has("terrainscan.scan.started")), "lang file loaded");
			check(!server.computeOnServer(s -> s.tickRateManager().isFrozen())
				&& !ctx.computeOnClient(mc -> mc.level.tickRateManager().isFrozen()), "multiplayer world not frozen");
			BlockPos me = ctx.computeOnClient(mc -> mc.player.blockPosition());
			y = me.getY();
			base = me.offset(4, 0, 4);
			int x = base.getX(), z = base.getZ();
			server.runCommand("fill %d %d %d %d %d %d stone".formatted(x, y, z, x + 2, y + 3, z + 2));
			server.runCommand("setblock %d %d %d oak_sign{front_text:{messages:[\"Terrain\",\"Scan\",\"\",\"\"]}}".formatted(x, y, z + 4));
			server.runCommand("setblock %d %d %d chest".formatted(x + 3, y, z));
			server.runCommand("item replace block %d %d %d container.0 with diamond".formatted(x + 3, y, z));
			server.runCommand("summon armor_stand %d.5 %d %d.5 {equipment:{head:{id:\"minecraft:diamond_helmet\",count:1}}}".formatted(x + 5, y, z + 5));
			server.runCommand("summon text_display %d.5 %d %d.5 {text:\"hello\"}".formatted(x, y + 5, z + 6));
			server.runCommand("summon zombie %d.5 %d %d.5 {NoAI:1b,PersistenceRequired:1b}".formatted(x - 3, y, z - 3));
			ctx.waitTicks(40);

			ctx.runOnClient(mc -> mc.player.connection.sendCommand("scan start srv 1 %d %d".formatted(y - 4, y + 8)));
			ctx.waitFor(mc -> Recorder.active != null && Recorder.active.pending().isEmpty() && !Recorder.active.done().isEmpty(), 400);
			ctx.waitTicks(5);
			ctx.takeScreenshot("terrainscan-recording");
			server.runCommand("tp @a 100 " + y + " 0");
			ctx.waitFor(mc -> Recorder.active.done().contains(net.minecraft.world.level.ChunkPos.pack(100 >> 4, 0)), 400);
			check(true, "follow mode scanned chunks around new position");
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("scan stop"));
			ctx.waitTick();
			check(Recorder.active == null, "recording stopped");
			String[] meta = read(ScanFiles.ROOT.resolve("srv/scan.txt")).trim().split(" ");
			center = new BlockPos(Integer.parseInt(meta[0]), y, Integer.parseInt(meta[1]));
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("scan start left 1"));
			ctx.waitTick();
			check(Recorder.active != null, "second scan left running");
		}

		try (var sp = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getServer().runCommand("tp @a 200 " + y + " 200");
			ctx.waitFor(mc -> mc.player.getX() > 199);
			BlockPos p = base;
			check(sp.getServer().computeOnServer(s -> s.tickRateManager().isFrozen()), "singleplayer world frozen on join");
			sp.getServer().runCommand("tick unfreeze");
			check(Recorder.active == null, "leaving the server stopped the scan");

			sp.getServer().runCommand("summon pig %d.5 %d %d.5 {NoAI:1b,PersistenceRequired:1b}".formatted(p.getX() - 3, y, p.getZ() - 3));
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("paste srv"));
			ctx.waitFor(mc -> Paster.jobs.isEmpty(), 600);
			ctx.waitTicks(10);
			BlockPos landed = ctx.computeOnClient(mc -> mc.player.blockPosition());
			check(landed.getX() == center.getX() && landed.getZ() == center.getZ() && landed.getY() > y - 5, "teleported on top of scan center, at " + landed);
			sp.getServer().runOnServer(s -> {
				ServerLevel l = s.overworld();
				check(l.getBlockState(p.offset(1, 3, 1)).is(Blocks.STONE), "stone pillar pasted at original coordinates");
				check(s.tickRateManager().isFrozen(), "paste froze the world again");
				check(l.getBlockEntity(p.offset(0, 0, 4)) instanceof SignBlockEntity sign
					&& sign.getText(SignTextSlot.FRONT).getMessages(false).get(1).getString().equals("Scan"), "sign text pasted");
				check(l.getBlockEntity(p.offset(3, 0, 0)) instanceof Container c && c.isEmpty(), "chest pasted without contents");
				var box = new AABB(p).inflate(12);
				var stands = l.getEntitiesOfClass(ArmorStand.class, box);
				check(stands.size() == 1 && stands.getFirst().getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "armor stand with helmet pasted once");
				check(l.getEntitiesOfClass(Display.TextDisplay.class, box).size() == 1, "text display pasted");
				check(l.getEntitiesOfClass(Zombie.class, box).isEmpty(), "zombie skipped");
			});
			ctx.runOnClient(mc -> { mc.player.setYRot(-45); mc.player.setXRot(20); });
			ctx.waitTicks(5);
			ctx.takeScreenshot("terrainscan-pasted");

			ctx.runOnClient(mc -> mc.player.connection.sendCommand("paste undo"));
			ctx.waitFor(mc -> Paster.jobs.isEmpty(), 600);
			ctx.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				ServerLevel l = s.overworld();
				check(l.getBlockState(p.offset(1, 3, 1)).isAir(), "undo removed pillar");
				check(l.getEntitiesOfClass(ArmorStand.class, new AABB(p).inflate(12)).isEmpty(), "undo removed armor stand");
				check(l.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, new AABB(p).inflate(12)).size() == 1, "undo kept mobs without duplicating them");
			});

			ctx.runOnClient(mc -> mc.player.connection.sendCommand("paste srv"));
			ctx.waitTick();
			check(!ctx.computeOnClient(mc -> Paster.jobs.isEmpty()), "paste left running");
		}
		ctx.waitFor(mc -> Paster.jobs.isEmpty() && Paster.lastPaste.isEmpty(), 200);
		check(true, "leaving the world cancelled the paste and cleared undo");
	}

	static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
		System.out.println("[terrainscan-test] OK: " + what);
	}

	static String read(java.nio.file.Path p) {
		try {
			return Files.readString(p);
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}
}
