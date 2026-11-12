package dev.terrainscan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class ScanFiles {
	static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve(TerrainScan.MOD_ID);
	private static final String META = "scan.txt";

	private ScanFiles() {}

	/** dim is null for scans saved before dimensions were recorded. */
	record Meta(int centerX, int centerZ, int minY, ResourceKey<Level> dim) {}

	static boolean validName(String name) {
		return name.matches("[A-Za-z0-9_-]+") && !name.equals("undo");
	}

	static Path dir(String name) {
		return ROOT.resolve(name);
	}

	static boolean exists(String name) {
		return validName(name) && Files.isRegularFile(dir(name).resolve(META));
	}

	static Path create(String name, BlockPos center, int minY, ResourceKey<Level> dim) throws IOException {
		Path dir = dir(name);
		if (Files.exists(dir)) clearDir(dir);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(META), center.getX() + " " + center.getZ() + " " + minY + " " + dim.identifier());
		return dir;
	}

	static Meta readMeta(String name) throws IOException {
		String[] m = Files.readString(dir(name).resolve(META)).trim().split(" ");
		var dim = m.length > 3 ? ResourceKey.create(Registries.DIMENSION, Identifier.parse(m[3])) : null;
		return new Meta(Integer.parseInt(m[0]), Integer.parseInt(m[1]), Integer.parseInt(m[2]), dim);
	}

	static Path chunkFile(Path dir, int cx, int cz) {
		return dir.resolve(cx + "." + cz + ".nbt");
	}

	static List<Path> chunkFiles(String name) throws IOException {
		try (Stream<Path> s = Files.list(dir(name))) {
			return s.filter(f -> f.getFileName().toString().endsWith(".nbt")).toList();
		}
	}

	static int[] chunkOf(Path file) {
		String[] xz = file.getFileName().toString().split("\\.");
		return new int[] {Integer.parseInt(xz[0]), Integer.parseInt(xz[1])};
	}

	static List<String> savedScans() {
		try (Stream<Path> s = Files.list(ROOT)) {
			return s.filter(p -> Files.isRegularFile(p.resolve(META))).map(p -> p.getFileName().toString()).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static void clearDir(Path dir) throws IOException {
		try (Stream<Path> s = Files.list(dir)) {
			for (Path p : (Iterable<Path>) s::iterator) Files.delete(p);
		}
	}
}
