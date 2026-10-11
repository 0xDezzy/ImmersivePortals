package qouteall.imm_ptl.peripheral.miniature;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import qouteall.q_misc_util.my_util.IntBox;
import java.util.UUID;

/** Operator prototype: no transferable item, destructive relocation or automatic building copies. */
public final class MiniatureBaseCommand {
    private MiniatureBaseCommand() {}
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        var miniature = Commands.literal("miniature").requires(source -> source.hasPermission(2));
        miniature.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
                .then(Commands.argument("divisor", DoubleArgumentType.doubleArg(2, 32))
                    .then(Commands.argument("position", Vec3Argument.vec3()).executes(context -> run(context, "create"))))))));
        miniature.then(Commands.literal("place").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("position", Vec3Argument.vec3()).executes(context -> run(context, "place")))));
        for (String operation : new String[]{"pickup", "restore"})
            miniature.then(Commands.literal(operation).then(Commands.argument("name", StringArgumentType.word()).executes(context -> run(context, operation))));
        miniature.then(Commands.literal("list").executes(context -> run(context, "list")));
        root.then(miniature);
    }
    private static int run(CommandContext<CommandSourceStack> context, String operation) throws CommandSyntaxException {
        var source = context.getSource(); var player = source.getPlayerOrException();
        var server = source.getServer(); var storage = MiniatureBaseStorage.get(server);
        try {
            if (operation.equals("list")) {
                for (MiniatureBase base : storage.entries()) if (base.owner().equals(player.getUUID()))
                    source.sendSuccess(() -> Component.literal(base.name() + " (" + (base.placed() ? "placed" : "picked up")
                        + ", 1:" + base.divisor() + ") backing=" + base.backingDimension().location() + " " + base.backing()), false);
                return 1;
            }
            String name = StringArgumentType.getString(context, "name");
            if (operation.equals("create")) {
                var box = new IntBox(BlockPosArgument.getBlockPos(context, "from"), BlockPosArgument.getBlockPos(context, "to")).toRealNumberBox();
                MiniatureBase base = new MiniatureBase(name, player.getUUID(), source.getLevel().dimension(), box,
                    source.getLevel().dimension(), Vec3Argument.getVec3(context, "position"), DoubleArgumentType.getDouble(context, "divisor"), true, UUID.randomUUID());
                MiniatureBaseManager.validatePlacement(server, base); storage.create(base);
            } else {
                MiniatureBase base = storage.owned(name, player.getUUID());
                switch (operation) {
                    case "place" -> {
                        if (base.placed()) throw new IllegalArgumentException("Pick up this miniature before placing it again.");
                        MiniatureBase placed = base.place(source.getLevel().dimension(), Vec3Argument.getVec3(context, "position"));
                        MiniatureBaseManager.validatePlacement(server, placed); storage.replace(placed, player.getUUID());
                    }
                    case "pickup" -> storage.replace(base.pickup(), player.getUUID());
                    case "restore" -> storage.remove(name, player.getUUID());
                    default -> throw new IllegalArgumentException("Unknown miniature operation.");
                }
            }
            MiniatureBaseManager.reconcile(server);
            source.sendSuccess(() -> Component.literal("Miniature " + name + ": " + operation + ". Backing blocks and occupants remain at their original coordinates."), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage())); return 0;
        }
    }
}
