package com.sb.peh.compat;

import com.sb.peh.PlanetEscapeHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.dries007.tfc.common.blocks.TFCBlockStateProperties;
import net.dries007.tfc.common.blocks.wood.BranchDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class SBLoggerHandler {
    private static final double PUSH_STRENGTH = 0.1;
    private static final int WINDOW_TICKS = 200;
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    private record Push(BlockPos pos, long gameTime, double dx, double dz) {}

    private static final Map<ResourceKey<Level>, Push> LAST = new HashMap<>();
    private static int loggedBreaks;
    private static boolean announcedPush;

    private SBLoggerHandler() {}

    // --- Push & Orientation Logic (formerly SBFallPush) ---

    public static void record(ServerPlayer player, BlockPos choppedPos) {
        Vec3 look = player.getLookAngle();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        if (length < 1.0E-4) {
            return;
        }
        Level level = player.level();
        LAST.put(level.dimension(), new Push(choppedPos.immutable(), level.getGameTime(), look.x / length, look.z / length));
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

    public static Direction.Axis fallAxis(Level level, BlockPos pos) {
        Push push = active(level, pos);
        if (push == null) {
            return null;
        }
        return Math.abs(push.dx()) >= Math.abs(push.dz()) ? Direction.Axis.X : Direction.Axis.Z;
    }

    public static void apply(Level level, BlockPos pos, Entity entity) {
        Push push = active(level, pos);
        if (push == null) {
            return;
        }
        entity.setDeltaMovement(entity.getDeltaMovement().add(push.dx() * PUSH_STRENGTH, 0.0, push.dz() * PUSH_STRENGTH));
        entity.hurtMarked = true;
        if (!announcedPush) {
            announcedPush = true;
            PlanetEscapeHandler.LOGGER.info("[physics] pushed a falling block along ({}, {})",
                    String.format("%.2f", push.dx()), String.format("%.2f", push.dz()));
        }
    }

    public static BlockState layDown(Level level, BlockPos pos, BlockState state) {
        boolean log = state.is(BlockTags.LOGS) || state.hasProperty(TFCBlockStateProperties.BRANCH_DIRECTION);
        if (!log
                || !state.hasProperty(BlockStateProperties.AXIS)
                || state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
            return state;
        }

        Direction.Axis axis = fallAxis(level, pos);
        if (axis == null) {
            axis = level.getRandom().nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        }
        state = state.setValue(BlockStateProperties.AXIS, axis);

        if (state.hasProperty(TFCBlockStateProperties.BRANCH_DIRECTION)) {
            Optional<BranchDirection> none = TFCBlockStateProperties.BRANCH_DIRECTION.getValue("none");
            if (none.isPresent()) {
                state = state.setValue(TFCBlockStateProperties.BRANCH_DIRECTION, none.get());
            }
        }
        return state;
    }

    // --- Failsafe Break & Drop Logic (formerly SBFallBreak) ---

    public static boolean resolve(FallingBlockEntity entity) {
        if (entity.dropItem || !entity.onGround() || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockState falling = entity.getBlockState();
        if (falling.isAir()) {
            return false;
        }

        BlockPos pos = entity.blockPosition();
        BlockState here = level.getBlockState(pos);
        DirectionalPlaceContext context = new DirectionalPlaceContext(level, pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP);
        boolean replaceable = here.canBeReplaced(context);
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        boolean freeBelow = FallingBlock.isFree(below);
        boolean survives = falling.canSurvive(level, pos);

        if (replaceable && survives && !freeBelow) {
            return false;
        }

        if (!replaceable && isBreakable(level, pos, here)) {
            level.destroyBlock(pos, true, entity);
            if (level.getBlockState(pos).canBeReplaced(context)) {
                logBreak("broke {} blocking {} at {}", id(here), id(falling), pos);
                return false;
            }
        }

        if (freeBelow && isBreakable(level, belowPos, below)) {
            level.destroyBlock(belowPos, true, entity);
            logBreak("broke {} under {} at {}", id(below), id(falling), belowPos);
            return false;
        }

        dropAndDiscard(level, entity, falling, pos);
        return true;
    }

    private static boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private static void dropAndDiscard(ServerLevel level, FallingBlockEntity entity, BlockState state, BlockPos pos) {
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                entity.getX(), entity.getY(), entity.getZ(), 20, 0.0, 0.05, 0.0, 0.1);
        if (level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            entity.spawnAtLocation(state.getBlock());
        }
        entity.discard();
        logBreak("nothing to break for {} at {}; dropped it", id(state), pos);
    }

    private static void logBreak(String message, Object... args) {
        if (loggedBreaks < 10) {
            loggedBreaks++;
            PlanetEscapeHandler.LOGGER.info("[physics] failsafe: " + message, args);
        }
    }

    private static String id(BlockState state) {
        return state.getBlock().getDescriptionId();
    }
}
