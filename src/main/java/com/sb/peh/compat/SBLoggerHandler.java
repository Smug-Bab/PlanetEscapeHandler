package com.sb.peh.compat;

import com.sb.peh.PlanetEscapeHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.dries007.tfc.common.blocks.TFCBlockStateProperties;
import net.dries007.tfc.common.blocks.wood.BranchDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class SBLoggerHandler {

    private static final double PUSH_STRENGTH = 0.1;
    private static final int WINDOW_TICKS = 200;
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    private record Push(
            BlockPos pos,
            long gameTime,
            double dx,
            double dz) {}

    private static final Map<ResourceKey<Level>, Push> LAST = new HashMap<>();

    private static boolean announcedPush;

    private SBLoggerHandler() {}

    public static void record(ServerPlayer player, BlockPos choppedPos) {
        Vec3 look = player.getLookAngle();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);

        if (length < 1.0E-4) {
            return;
        }

        Level level = player.level();

        LAST.put(
                level.dimension(),
                new Push(
                        choppedPos.immutable(),
                        level.getGameTime(),
                        look.x / length,
                        look.z / length));
    }

    private static Push active(Level level, BlockPos pos) {
        Push push = LAST.get(level.dimension());

        if (push == null
                || level.getGameTime() - push.gameTime() > WINDOW_TICKS
                || push.pos().distSqr(pos) > MAX_DISTANCE_SQ) {
            return null;
        }

        return push;
    }

    public static BlockState layDown(
            Level level,
            BlockPos pos,
            BlockState state) {

        Push push = active(level, pos);

        // Leave normal Simple Block Physics behavior unchanged unless this
        // falling block is near a recent TreeChop action.
        if (push == null) {
            return state;
        }

        boolean log = state.is(BlockTags.LOGS)
                || state.hasProperty(
                        TFCBlockStateProperties.BRANCH_DIRECTION);

        if (!log
                || !state.hasProperty(BlockStateProperties.AXIS)
                || state.getValue(BlockStateProperties.AXIS)
                        != Direction.Axis.Y) {
            return state;
        }

        Direction.Axis axis =
                Math.abs(push.dx()) >= Math.abs(push.dz())
                        ? Direction.Axis.X
                        : Direction.Axis.Z;

        state = state.setValue(BlockStateProperties.AXIS, axis);

        if (state.hasProperty(TFCBlockStateProperties.BRANCH_DIRECTION)) {
            Optional<BranchDirection> none =
                    TFCBlockStateProperties.BRANCH_DIRECTION
                            .getValue("none");

            if (none.isPresent()) {
                state = state.setValue(
                        TFCBlockStateProperties.BRANCH_DIRECTION,
                        none.get());
            }
        }

        return state;
    }

    public static void apply(
            Level level,
            BlockPos pos,
            Entity entity) {

        if (entity == null) {
            return;
        }

        Push push = active(level, pos);

        if (push == null) {
            return;
        }

        entity.setDeltaMovement(
                entity.getDeltaMovement().add(
                        push.dx() * PUSH_STRENGTH,
                        0.0,
                        push.dz() * PUSH_STRENGTH));

        entity.hurtMarked = true;

        if (!announcedPush) {
            announcedPush = true;
            PlanetEscapeHandler.LOGGER.info(
                    "[physics] Applied TreeChop directional velocity");
        }
    }
}