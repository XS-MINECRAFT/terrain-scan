package dev.terrainscan;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

final class Decorations {
	static final Set<String> KEEP = Set.of(
		"minecraft:armor_stand", "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting",
		"minecraft:block_display", "minecraft:item_display", "minecraft:text_display");

	private Decorations() {}

	static CompoundTag filter(CompoundTag template) {
		ListTag kept = new ListTag();
		for (var e : template.getListOrEmpty("entities")) {
			var info = (CompoundTag) e;
			var pos = info.getListOrEmpty("pos");
			double x = pos.getDoubleOr(0, -1), z = pos.getDoubleOr(2, -1);
			if (x >= 0 && x < 16 && z >= 0 && z < 16
				&& KEEP.contains(info.getCompoundOrEmpty("nbt").getStringOr("id", "")))
				kept.add(info);
		}
		template.put("entities", kept);
		return template;
	}

	static void clear(ServerLevel level, BlockPos origin, Vec3i size) {
		var box = new AABB(origin.getX(), origin.getY(), origin.getZ(),
			origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
		for (Entity e : level.getEntities((Entity) null, box,
				e -> KEEP.contains(EntityType.getKey(e.getType()).toString()) && box.contains(e.position())))
			e.discard();
	}
}
