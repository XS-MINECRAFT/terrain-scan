package dev.terrainscan;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;

final class ScanCommands {
	private ScanCommands() {}

	static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("scan")
			.then(literal("start").then(argument("name", StringArgumentType.word())
				.then(argument("radius", IntegerArgumentType.integer(0, 32))
					.executes(c -> Recorder.start(c.getSource(), name(c), radius(c), null, null))
					.then(argument("minY", IntegerArgumentType.integer())
						.then(argument("maxY", IntegerArgumentType.integer())
							.executes(c -> Recorder.start(c.getSource(), name(c), radius(c),
								IntegerArgumentType.getInteger(c, "minY"), IntegerArgumentType.getInteger(c, "maxY"))))))))
			.then(literal("stop").executes(c -> Recorder.stop(c.getSource()))));
		d.register(literal("paste")
			.then(literal("undo").executes(c -> Paster.undo(c.getSource())))
			.then(argument("name", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(ScanFiles.savedScans(), b))
				.executes(c -> Paster.paste(c.getSource(), name(c)))));
	}

	private static String name(CommandContext<?> c) {
		return StringArgumentType.getString(c, "name");
	}

	private static int radius(CommandContext<?> c) {
		return IntegerArgumentType.getInteger(c, "radius");
	}
}
