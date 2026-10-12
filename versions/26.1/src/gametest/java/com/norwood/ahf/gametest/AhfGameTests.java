package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.lagcomp.LagCompBench;
import com.norwood.ahf.stance.StanceBench;
import com.norwood.ahf.stance.StanceSyncGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** 26.1 gametests are registry entries: one test function + one function test instance per {@link AhfGameTest}. */
@EventBusSubscriber(modid = Ahf.MOD_ID)
public final class AhfGameTests {

    private static final Class<?>[] HOLDERS = {
            HitApiGameTest.class, HitLocationFallbackGameTest.class, HitLocationGameTest.class,
            LagCompGameTest.class, LimbRigGameTest.class, RigCacheGameTest.class, RigYawGameTest.class,
            StanceGameTest.class, StanceSyncGameTest.class, LagCompBench.class, StanceBench.class
    };
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(Ahf.MOD_ID, "empty");

    private AhfGameTests() {
    }

    private static Identifier id(Method m) {
        return Identifier.fromNamespaceAndPath(Ahf.MOD_ID, (m.getDeclaringClass().getSimpleName() + "."
                + m.getName()).toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (Class<?> c : HOLDERS) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(AhfGameTest.class)) {
                        helper.register(id(m), function(m));
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        for (Class<?> c : HOLDERS) {
            for (Method m : c.getDeclaredMethods()) {
                AhfGameTest t = m.getAnnotation(AhfGameTest.class);
                if (t == null) {
                    continue;
                }
                Holder<TestEnvironmentDefinition<?>> env = environments.computeIfAbsent(t.batch(),
                        b -> event.registerEnvironment(Identifier.fromNamespaceAndPath(Ahf.MOD_ID, b)));
                event.registerTest(id(m), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,
                        id(m)), new TestData<>(env, TEMPLATE, t.timeoutTicks(), 0, t.required())));
            }
        }
    }

    private static Consumer<GameTestHelper> function(Method m) {
        return helper -> {
            try {
                Object holder = Modifier.isStatic(m.getModifiers()) ? null
                        : m.getDeclaringClass().getDeclaredConstructor().newInstance();
                m.invoke(holder, helper);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) {
                    throw r;
                }
                if (e.getCause() instanceof Error err) {
                    throw err;
                }
                throw new IllegalStateException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
