package com.sb.peh.compat;

import com.mojang.logging.LogUtils;
import com.sb.peh.PlanetEscapeHandler;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * Pushes Sable sublevels along TFC water currents.
 * Requires Sable AND its companion jar on the compile classpath.
 */
@EventBusSubscriber(modid = PlanetEscapeHandler.MODID)
public final class SableRiverInfluencer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double PHYSICS_SECONDS_PER_TICK = 0.05;
    private static final double CURRENT_ACCELERATION_PER_TICK = 0.28;
    private static final double MAX_CURRENT_SPEED = 2.0;
    private static final int MAX_HORIZONTAL_SAMPLES = 5;
    private static final int MAX_VERTICAL_SAMPLES = 3;
    private static final double FLOW_EPSILON = 1.0E-6;

    private static boolean loggedFirstTick;
    private static boolean warned;

    private SableRiverInfluencer() {
    }

    @SubscribeEvent
    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        try {
            if (!loggedFirstTick) {
                loggedFirstTick = true;
                LOGGER.info("Sable physics tick received; direct integration OK");
            }

            SubLevelPhysicsSystem physicsSystem = event.getPhysicsSystem();
            if (physicsSystem == null) {
                return;
            }

            ServerLevel level = physicsSystem.getLevel();
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container == null) {
                return;
            }

            double timeStep = event.getTimeStep();
            Iterable<ServerSubLevel> subLevels = container.getAllSubLevels();
            if (subLevels == null) {
                return;
            }

            for (ServerSubLevel subLevel : subLevels) {
                applyCurrentToSubLevel(level, physicsSystem, subLevel, timeStep);
            }
        } catch (Exception ex) {
            // Runs every physics tick, so only log the first failure.
            if (!warned) {
                warned = true;
                LOGGER.warn("Unable to apply currents to Sable sublevels", ex);
            }
        }
    }

    private static void applyCurrentToSubLevel(ServerLevel level, SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel, double timeStep) {
        if (subLevel.isRemoved()) {
            return;
        }

        BoundingBox3dc boundingBox = subLevel.boundingBox();
        Vec3 current = sampleCurrent(level, boundingBox);
        double strength = current.length();
        if (strength <= FLOW_EPSILON) {
            return;
        }

        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        if (handle == null || !handle.isValid()) {
            return;
        }

        Vec3 direction = current.scale(1.0D / strength);
        Vector3d velocity = new Vector3d();
        handle.getLinearVelocity(velocity);
        if (!isFinite(velocity.x) || !isFinite(velocity.y) || !isFinite(velocity.z)) {
            return;
        }

        double currentSpeed = velocity.x * direction.x + velocity.y * direction.y + velocity.z * direction.z;
        double remainingSpeed = MAX_CURRENT_SPEED - currentSpeed;
        if (remainingSpeed <= 0.0D) {
            return;
        }

        double timeScale = Math.max(0.0D, timeStep / PHYSICS_SECONDS_PER_TICK);
        double deltaSpeed = Math.min(CURRENT_ACCELERATION_PER_TICK * timeScale * Math.min(strength, 1.0D), remainingSpeed);
        if (deltaSpeed <= 0.0D) {
            return;
        }

        Vector3d linearDelta = new Vector3d(
                direction.x * deltaSpeed,
                direction.y * deltaSpeed,
                direction.z * deltaSpeed);
        handle.addLinearAndAngularVelocity(linearDelta, new Vector3d());
    }

    private static Vec3 sampleCurrent(ServerLevel level, BoundingBox3dc boundingBox) {
        double minX = boundingBox.minX();
        double minY = boundingBox.minY();
        double minZ = boundingBox.minZ();
        double maxX = boundingBox.maxX();
        double maxY = boundingBox.maxY();
        double maxZ = boundingBox.maxZ();
        if (!isUsableBox(minX, minY, minZ, maxX, maxY, maxZ)) {
            return Vec3.ZERO;
        }

        double topY = Math.min(maxY, minY + 2.0D);
        int xSamples = sampleCount(maxX - minX, MAX_HORIZONTAL_SAMPLES);
        int ySamples = sampleCount(topY - minY, MAX_VERTICAL_SAMPLES);
        int zSamples = sampleCount(maxZ - minZ, MAX_HORIZONTAL_SAMPLES);
        int samplePositions = 0;
        Vec3 total = Vec3.ZERO;

        for (int xi = 0; xi < xSamples; ++xi) {
            double x = sampleCoordinate(minX, maxX, xSamples, xi);
            for (int yi = 0; yi < ySamples; ++yi) {
                double y = sampleCoordinate(minY, topY, ySamples, yi);
                for (int zi = 0; zi < zSamples; ++zi) {
                    double z = sampleCoordinate(minZ, maxZ, zSamples, zi);
                    ++samplePositions;
                    BlockPos pos = BlockPos.containing(x, y, z);
                    FluidState fluid = level.getFluidState(pos);
                    if (!fluid.isEmpty()) {
                        Vec3 flow = fluid.getFlow(level, pos);
                        if (flow.lengthSqr() > FLOW_EPSILON) {
                            total = total.add(flow);
                        }
                    }
                }
            }
        }

        return samplePositions == 0 ? Vec3.ZERO : total.scale(1.0D / samplePositions);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isUsableBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return isFinite(minX)
                && isFinite(minY)
                && isFinite(minZ)
                && isFinite(maxX)
                && isFinite(maxY)
                && isFinite(maxZ)
                && maxX > minX
                && maxY > minY
                && maxZ > minZ;
    }

    private static int sampleCount(double span, int maximum) {
        return Math.max(1, Math.min(maximum, (int) Math.ceil(span) + 1));
    }

    private static double sampleCoordinate(double min, double max, int samples, int index) {
        return samples <= 1 ? (min + max) * 0.5D : min + (max - min) * (double) index / (double) (samples - 1);
    }
}
