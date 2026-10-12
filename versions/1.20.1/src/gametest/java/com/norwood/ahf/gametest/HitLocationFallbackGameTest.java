package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.hit.HitCategory;
import com.norwood.ahf.hit.HitSampler;
import com.norwood.ahf.part.HitboxPart;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** {@link HitSampler} weighted fallback: no attacker, no projectile => no geometry. */
@GameTestHolder(Ahf.MOD_ID)
@PrefixGameTestTemplate(false)
public class HitLocationFallbackGameTest {

    private static final String TEMPLATE = "empty";
    private static final int SAMPLES = 6000;

    private static DamageSource directionless(GameTestHelper helper) {
        Holder<DamageType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC);
        return new DamageSource(type);
    }

    private static Map<HitboxPart, Integer> distribution(GameTestHelper helper, HitCategory cat) {
        DamageSource src = directionless(helper);
        LivingEntity v = Bodies.player(helper);
        RandomSource rand = RandomSource.create(0x5EEDL);
        Map<HitboxPart, Integer> counts = new EnumMap<>(HitboxPart.class);
        for (HitboxPart p : HitboxPart.VALUES) {
            counts.put(p, 0);
        }
        for (int i = 0; i < SAMPLES; i++) {
            HitboxPart picked = HitSampler.pick(v, src, cat, rand);
            if (picked == null) {
                helper.fail("pick returned null for " + cat);
            }
            counts.merge(picked, 1, Integer::sum);
        }
        return counts;
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aDirectionlessHitReallyDoesFallBackToWeightedSampling(GameTestHelper helper) {
        long distinct = distribution(helper, HitCategory.GENERIC).values().stream().filter(n -> n > 0).count();
        helper.assertTrue(distinct >= 5, "only " + distinct + " parts ever picked: not a weighted sample");
        helper.succeed();
    }

    /** Torso bias 0.3 => legs ~84%; 0.6 => ~73% (fails the 80% floor). */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aFallLandsOnTheLegsAndNeverOnTheHeadOrArms(GameTestHelper helper) {
        Map<HitboxPart, Integer> counts = distribution(helper, HitCategory.FALL);
        helper.assertTrue(counts.get(HitboxPart.HEAD) == 0 && counts.get(HitboxPart.LEFT_ARM) == 0
                && counts.get(HitboxPart.RIGHT_ARM) == 0, "a landing hit a head or arm: " + counts);
        int legs = counts.get(HitboxPart.LEFT_LEG) + counts.get(HitboxPart.RIGHT_LEG);
        helper.assertTrue(legs >= SAMPLES * 0.8, "legs " + legs + "/" + SAMPLES + ": " + counts);
        helper.assertTrue(counts.get(HitboxPart.TORSO) > 0, "torso unreachable on a fall: " + counts);
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void gunfireFavoursCentreMassAndTheHeadOverTheLimbs(GameTestHelper helper) {
        Map<HitboxPart, Integer> ballistic = distribution(helper, HitCategory.BALLISTIC);
        Map<HitboxPart, Integer> generic = distribution(helper, HitCategory.GENERIC);
        helper.assertTrue(ballistic.get(HitboxPart.TORSO) > generic.get(HitboxPart.TORSO),
                "ballistic torso " + ballistic + " vs generic " + generic);
        helper.assertTrue(ballistic.get(HitboxPart.HEAD) > generic.get(HitboxPart.HEAD),
                "ballistic head " + ballistic + " vs generic " + generic);
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void anExplosionFavoursTheLimbsOverTheVitals(GameTestHelper helper) {
        Map<HitboxPart, Integer> blast = distribution(helper, HitCategory.EXPLOSION);
        Map<HitboxPart, Integer> generic = distribution(helper, HitCategory.GENERIC);
        int blastVital = blast.get(HitboxPart.HEAD) + blast.get(HitboxPart.TORSO);
        int genericVital = generic.get(HitboxPart.HEAD) + generic.get(HitboxPart.TORSO);
        helper.assertTrue(blastVital < genericVital, "blast vitals " + blastVital + " vs generic " + genericVital);
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void theSamplerIsDeterministicForAGivenSeed(GameTestHelper helper) {
        DamageSource src = directionless(helper);
        LivingEntity v = Bodies.player(helper);
        for (int seed = 0; seed < 20; seed++) {
            HitboxPart a = HitSampler.pick(v, src, HitCategory.GENERIC, RandomSource.create(seed));
            HitboxPart b = HitSampler.pick(v, src, HitCategory.GENERIC, RandomSource.create(seed));
            helper.assertTrue(a == b, "seed " + seed + " gave " + a + " then " + b);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void withNoRandomSourceThePickIsTheTorso(GameTestHelper helper) {
        HitboxPart picked = HitSampler.pick(Bodies.player(helper), directionless(helper), HitCategory.GENERIC, null);
        helper.assertTrue(picked == HitboxPart.TORSO, "no-randomness fallback gave " + picked);
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aNullVictimStillYieldsAPart(GameTestHelper helper) {
        helper.assertTrue(HitSampler.pick(null, directionless(helper), HitCategory.BALLISTIC,
                RandomSource.create(7L)) != null, "pick returned null with no victim");
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void pickPiercedAlwaysReturnsAtLeastOnePart(GameTestHelper helper) {
        LivingEntity v = Bodies.player(helper);
        DamageSource src = directionless(helper);
        for (HitCategory cat : HitCategory.values()) {
            List<HitboxPart> pierced = HitSampler.pickPierced(v, src, cat, RandomSource.create(3L));
            helper.assertTrue(!pierced.isEmpty() && pierced.stream().allMatch(java.util.Objects::nonNull),
                    "pickPierced " + cat + ": " + pierced);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void everyCategoryCanBeSampledWithoutThrowing(GameTestHelper helper) {
        LivingEntity v = Bodies.player(helper);
        DamageSource src = directionless(helper);
        RandomSource rand = RandomSource.create(11L);
        for (HitCategory cat : HitCategory.values()) {
            for (int i = 0; i < 200; i++) {
                helper.assertTrue(HitSampler.pick(v, src, cat, rand) != null, "pick returned null for " + cat);
            }
        }
        helper.succeed();
    }
}
