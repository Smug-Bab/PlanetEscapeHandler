package com.sb.peh.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;
import org.slf4j.Logger;

public final class SableCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SableBridge BRIDGE = new SableBridge();

    private SableCompat() {
    }

    public static void register() {
        final String name = "dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent";
        try {
            Class<?> eventClass = Class.forName(name, false, SableCompat.class.getClassLoader());
            if (!Event.class.isAssignableFrom(eventClass)) {
                LOGGER.warn("{} is not a NeoForge Event; compat disabled", name);
                return;
            }
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, eventClass.asSubclass(Event.class), SableCompat::apply);
            LOGGER.info("Hooked {}", name);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Sable event class not found; is Sable installed, and is it the version this targets?");
        } catch (LinkageError e) {
            LOGGER.warn("Failed to load Sable event class", e);
        }
    }

    private static void apply(Event event) {
        BRIDGE.apply(event);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class SableBridge {
        private static final double PHYSICS_SECONDS_PER_TICK = 0.05;
        private static final double CURRENT_ACCELERATION_PER_TICK = 0.28;
        private static final double MAX_CURRENT_SPEED = 2.0;
        private static final int MAX_HORIZONTAL_SAMPLES = 5;
        private static final int MAX_VERTICAL_SAMPLES = 3;
        private static final double FLOW_EPSILON = 1.0E-6;

        private boolean initialized;
        private boolean available;
        private boolean warned;
                private boolean loggedFirstTick;
        private Method eventGetPhysicsSystem;
        private Method eventGetTimeStep;
        private Method physicsGetLevel;
        private Method getContainer;
        private Method getAllSubLevels;
        private Method getPhysicsHandle;
        private Method subLevelIsRemoved;
        private Method subLevelBoundingBox;
        private Method handleIsValid;
        private Method getLinearVelocity;
        private Method addLinearAndAngularVelocity;
        private Method boxMinX;
        private Method boxMinY;
        private Method boxMinZ;
        private Method boxMaxX;
        private Method boxMaxY;
        private Method boxMaxZ;

        void apply(Event event) {
            try {
                if (!initialize(event.getClass())) {
                    return;
                }

                if (!loggedFirstTick) {
                    loggedFirstTick = true;
                    LOGGER.info("Sable physics tick received; reflection init OK");
                }

                Object physicsSystem = eventGetPhysicsSystem.invoke(event);
                if (physicsSystem == null) {
                    return;
                }

                ServerLevel level = (ServerLevel) physicsGetLevel.invoke(physicsSystem);
                Object container = getContainer.invoke(null, level);
                if (container == null) {
                    return;
                }

                double timeStep = (double) eventGetTimeStep.invoke(event);
                Object subLevelsObject = getAllSubLevels.invoke(container);
                if (!(subLevelsObject instanceof Iterable<?> subLevels)) {
                    return;
                }

                for (Object subLevel : subLevels) {
                    applyCurrentToSubLevel(level, physicsSystem, subLevel, timeStep);
                }
            } catch (LinkageError | RuntimeException | ReflectiveOperationException ex) {
                warnOnce("Unable to apply currents to Sable sublevels", ex);
            }
        }

        private boolean initialize(Class<?> eventClass) throws ReflectiveOperationException {
            if (initialized) {
                return available;
            }

            initialized = true;
            ClassLoader classLoader = SableCompat.class.getClassLoader();
            Class<?> physicsSystemClass = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", false, classLoader);
            Class<?> subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", false, classLoader);
            Class<?> serverSubLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", false, classLoader);
            Class<?> serverSubLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel", false, classLoader);
            Class<?> rigidBodyHandleClass = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle", false, classLoader);
            Class<?> boundingBoxClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc", false, classLoader);
            Class<?> vector3dcClass = Class.forName("org.joml.Vector3dc", false, classLoader);

            eventGetPhysicsSystem = eventClass.getMethod("getPhysicsSystem");
            eventGetTimeStep = eventClass.getMethod("getTimeStep");
            physicsGetLevel = physicsSystemClass.getMethod("getLevel");
            getContainer = subLevelContainerClass.getMethod("getContainer", ServerLevel.class);
            getAllSubLevels = serverSubLevelContainerClass.getMethod("getAllSubLevels");
            getPhysicsHandle = physicsSystemClass.getMethod("getPhysicsHandle", serverSubLevelClass);
            subLevelIsRemoved = serverSubLevelClass.getMethod("isRemoved");
            subLevelBoundingBox = serverSubLevelClass.getMethod("boundingBox");
            handleIsValid = rigidBodyHandleClass.getMethod("isValid");
            getLinearVelocity = rigidBodyHandleClass.getMethod("getLinearVelocity", Vector3d.class);
            addLinearAndAngularVelocity = rigidBodyHandleClass.getMethod("addLinearAndAngularVelocity", vector3dcClass, vector3dcClass);
            boxMinX = boundingBoxClass.getMethod("minX");
            boxMinY = boundingBoxClass.getMethod("minY");
            boxMinZ = boundingBoxClass.getMethod("minZ");
            boxMaxX = boundingBoxClass.getMethod("maxX");
            boxMaxY = boundingBoxClass.getMethod("maxY");
            boxMaxZ = boundingBoxClass.getMethod("maxZ");
            available = true;
            return true;
        }

        private void applyCurrentToSubLevel(ServerLevel level, Object physicsSystem, Object subLevel, double timeStep)
                throws ReflectiveOperationException {
            if ((boolean) subLevelIsRemoved.invoke(subLevel)) {
                return;
            }

            Object boundingBox = subLevelBoundingBox.invoke(subLevel);
            Vec3 current = sampleCurrent(level, boundingBox);
            double strength = current.length();
            if (!(strength <= FLOW_EPSILON)) {
                Object handle = getPhysicsHandle.invoke(physicsSystem, subLevel);
                if (handle != null && (boolean) handleIsValid.invoke(handle)) {
                    Vec3 direction = current.scale(1.0D / strength);
                    Vector3d velocity = new Vector3d();
                    getLinearVelocity.invoke(handle, velocity);
                    if (isFinite(velocity.x) && isFinite(velocity.y) && isFinite(velocity.z)) {
                        double currentSpeed = velocity.x * direction.x + velocity.y * direction.y + velocity.z * direction.z;
                        double remainingSpeed = MAX_CURRENT_SPEED - currentSpeed;
                        if (!(remainingSpeed <= 0.0D)) {
                            double timeScale = Math.max(0.0D, timeStep / PHYSICS_SECONDS_PER_TICK);
                            double deltaSpeed = Math.min(CURRENT_ACCELERATION_PER_TICK * timeScale * Math.min(strength, 1.0D), remainingSpeed);
                            if (!(deltaSpeed <= 0.0D)) {
                                Vector3d linearDelta = new Vector3d(
                                        direction.x * deltaSpeed,
                                        direction.y * deltaSpeed,
                                        direction.z * deltaSpeed);
                                addLinearAndAngularVelocity.invoke(handle, linearDelta, new Vector3d());
                            }
                        }
                    }
                }
            }
        }

        private Vec3 sampleCurrent(ServerLevel level, Object boundingBox) throws ReflectiveOperationException {
            double minX = (double) boxMinX.invoke(boundingBox);
            double minY = (double) boxMinY.invoke(boundingBox);
            double minZ = (double) boxMinZ.invoke(boundingBox);
            double maxX = (double) boxMaxX.invoke(boundingBox);
            double maxY = (double) boxMaxY.invoke(boundingBox);
            double maxZ = (double) boxMaxZ.invoke(boundingBox);
            if (!isUsableBox(minX, minY, minZ, maxX, maxY, maxZ)) {
                return Vec3.ZERO;
            }

            int xSamples = sampleCount(maxX - minX, MAX_HORIZONTAL_SAMPLES);
            int ySamples = sampleCount(Math.min(maxY, minY + 2.0D) - minY, MAX_VERTICAL_SAMPLES);
            int zSamples = sampleCount(maxZ - minZ, MAX_HORIZONTAL_SAMPLES);
            int samplePositions = 0;
            Vec3 total = Vec3.ZERO;

            for (int xi = 0; xi < xSamples; ++xi) {
                double x = sampleCoordinate(minX, maxX, xSamples, xi);
                for (int yi = 0; yi < ySamples; ++yi) {
                    double y = sampleCoordinate(minY, Math.min(maxY, minY + 2.0D), ySamples, yi);
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

        private void warnOnce(String message, Throwable throwable) {
            if (warned) {
                return;
            }
            warned = true;
            Throwable cause = throwable;
            if (throwable instanceof InvocationTargetException invocation && invocation.getCause() != null) {
                cause = invocation.getCause();
            }
            LOGGER.warn(message, cause);
        }
    }
}
