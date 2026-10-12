package com.norwood.ahf.config;

import com.norwood.ahf.hit.HitAuthority;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.RigTuning;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class AhfConfigSpec {

    public static final ModConfigSpec SPEC;
    private static final double MAX_REACH = 1.0;

    private static final ModConfigSpec.BooleanValue GEOMETRIC_HIT_LOCATION;
    private static final ModConfigSpec.BooleanValue POSE_AWARE_ARMS;
    private static final ModConfigSpec.DoubleValue HEAD_BAND_BOTTOM;
    private static final ModConfigSpec.DoubleValue LEG_BAND_TOP;
    private static final ModConfigSpec.DoubleValue ARM_SIDE_THRESHOLD;
    private static final ModConfigSpec.DoubleValue MELEE_REACH;

    private static final ModConfigSpec.BooleanValue RIGGED_LIMB_BOXES;
    private static final ModConfigSpec.DoubleValue LIMB_BOX_PADDING;
    private static final ModConfigSpec.BooleanValue GUN_ARM_POSE;
    private static final ModConfigSpec.BooleanValue ANIMATED_HITBOXES;

    private static final ModConfigSpec.EnumValue<HitRegMode> HIT_REGISTRATION_MODE;
    private static final ModConfigSpec.DoubleValue HIT_GAP_REJECT_TOLERANCE;
    private static final ModConfigSpec.DoubleValue[] ENV_REACH_H;
    private static final ModConfigSpec.DoubleValue[] ENV_REACH_V;
    private static final ModConfigSpec.DoubleValue STANCE_ENV_REACH_H;
    private static final ModConfigSpec.DoubleValue STANCE_ENV_REACH_V;

    private static final ModConfigSpec.EnumValue<HitAuthority> HIT_AUTHORITY;
    private static final ModConfigSpec.IntValue POSE_STREAM_MIN_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue POSE_STREAM_MAX_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue POSE_STREAM_CHANGE_EPSILON;
    private static final ModConfigSpec.IntValue POSE_HINT_MAX_AGE_TICKS;
    private static final ModConfigSpec.DoubleValue POSE_HINT_MARGIN;

    private static final ModConfigSpec.BooleanValue PENETRATION_ENABLED;
    private static final ModConfigSpec.DoubleValue PENETRATION_BUDGET;
    private static final ModConfigSpec.DoubleValue PENETRATION_ENERGY_FALLOFF;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_HEAD;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_TORSO;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_ARM;
    private static final ModConfigSpec.DoubleValue PEN_RESIST_LEG;

    private static final ModConfigSpec.BooleanValue LAG_COMPENSATION;
    private static final ModConfigSpec.IntValue LAG_COMP_MAX_REWIND_TICKS;
    private static final ModConfigSpec.IntValue LAG_COMP_INTERP_TICKS;
    private static final ModConfigSpec.IntValue LAG_COMP_SLACK_TICKS;
    private static final ModConfigSpec.DoubleValue LAG_COMP_NON_PLAYER_RANGE;
    private static final ModConfigSpec.IntValue RTT_PROBE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue RTT_WINDOW_SAMPLES;

    private static final ModConfigSpec.BooleanValue HITBOX_DEBUG;
    private static final ModConfigSpec.BooleanValue LOG_HIT_DETECTION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("hitLocation");
        GEOMETRIC_HIT_LOCATION = b.comment("false => weighted random part, geometry ignored.")
                .define("geometricHitLocation", AhfConfig.geometricHitLocation());
        POSE_AWARE_ARMS = b.comment("Banded fallback: front hits at upper-arm height on an aiming victim -> arm.")
                .define("poseAwareArms", AhfConfig.poseAwareArms());
        HEAD_BAND_BOTTOM = b.comment("Banded fallback: head at or above this fraction of box height.")
                .defineInRange("headBandBottom", AhfConfig.headBandBottom(), 0.0, 1.0);
        LEG_BAND_TOP = b.comment("Banded fallback: legs at or below this fraction of box height.")
                .defineInRange("legBandTop", AhfConfig.legBandTop(), 0.0, 1.0);
        ARM_SIDE_THRESHOLD = b.comment("Banded fallback: |side| / half-width at or above this -> arm.")
                .defineInRange("armSideThreshold", AhfConfig.armSideThreshold(), 0.0, 1.0);
        MELEE_REACH = b.comment("Melee attack segment length (blocks).")
                .defineInRange("meleeReach", AhfConfig.meleeReach(), 0.0, 16.0);
        b.pop();

        b.push("rig");
        RIGGED_LIMB_BOXES = b.comment("false => players use the banded fallback only.")
                .define("riggedLimbBoxes", AhfConfig.riggedLimbBoxes());
        LIMB_BOX_PADDING = b.comment("Grows every limb box by this many blocks per side.")
                .defineInRange("limbBoxPadding", AhfConfig.limbBoxPadding(), 0.0, 0.5);
        GUN_ARM_POSE = b.comment("Held gun -> hold/ADS arm pose; false => bow pose.")
                .define("gunArmPose", AhfConfig.gunArmPose());
        ANIMATED_HITBOXES = b.comment("PlayerAnimator bones drive hitboxes. Reaches the server only via the client",
                        "pose stream => turns streaming on under SERVER authority too; same client trust as CLIENT_HINT.")
                .define("animatedHitboxes", AhfConfig.animatedHitboxes());
        b.pop();

        b.push("hitRegistration");
        HIT_REGISTRATION_MODE = b.comment("OFF: vanilla box. ENVELOPE: box widened per pose. PRECISE: ENVELOPE, then",
                        "ranged shots clearing every limb box by > hitGapRejectTolerance are rejected.",
                        "Player melee is gap-tested in ENVELOPE and PRECISE.")
                .defineEnum("hitRegistrationMode", AhfConfig.hitRegistrationMode());
        HIT_GAP_REJECT_TOLERANCE = b.comment("Gap rejection pad (blocks); 0 = reject unless the ray enters a limb box.")
                .defineInRange("hitGapRejectTolerance", AhfConfig.hitGapRejectTolerance(), 0.0, 2.0);
        b.comment("Blocks per side. Max 1.0: vanilla picks gather candidates from a search box inflated by 1.0.")
                .push("envelopeReach");
        int poses = RigTuning.RigPose.VALUES.length;
        ENV_REACH_H = new ModConfigSpec.DoubleValue[poses];
        ENV_REACH_V = new ModConfigSpec.DoubleValue[poses];
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            String n = pose.lower();
            ENV_REACH_H[pose.ordinal()] = b.defineInRange(n + "Horizontal",
                    AhfConfig.hitEnvelopeReachHorizontal(pose), 0.0, MAX_REACH);
            ENV_REACH_V[pose.ordinal()] = b.defineInRange(n + "Vertical",
                    AhfConfig.hitEnvelopeReachVertical(pose), 0.0, MAX_REACH);
        }
        STANCE_ENV_REACH_H = b.comment("Default for a Stance that does not override its reach.")
                .defineInRange("stanceHorizontal", AhfConfig.stanceEnvelopeReachHorizontal(), 0.0, MAX_REACH);
        STANCE_ENV_REACH_V = b.defineInRange("stanceVertical", AhfConfig.stanceEnvelopeReachVertical(), 0.0, MAX_REACH);
        b.pop();
        b.pop();

        b.push("authority");
        HIT_AUTHORITY = b.comment("SERVER: server rebuilds the rig. CLIENT_HINT: victim streams its rig; server",
                        "validates it and still runs the ray test.")
                .defineEnum("hitAuthority", AhfConfig.hitAuthority());
        POSE_STREAM_MIN_INTERVAL_TICKS = b.defineInRange("poseStreamMinIntervalTicks",
                AhfConfig.poseStreamMinIntervalTicks(), 1, 40);
        POSE_STREAM_MAX_INTERVAL_TICKS = b.comment("Heartbeat; keep < poseHintMaxAgeTicks.")
                .defineInRange("poseStreamMaxIntervalTicks", AhfConfig.poseStreamMaxIntervalTicks(), 1, 200);
        POSE_STREAM_CHANGE_EPSILON = b.comment("Resend once any streamed box scalar drifts more than this.")
                .defineInRange("poseStreamChangeEpsilon", AhfConfig.poseStreamChangeEpsilon(), 0.0, 1.0);
        POSE_HINT_MAX_AGE_TICKS = b.defineInRange("poseHintMaxAgeTicks", AhfConfig.poseHintMaxAgeTicks(), 1, 200);
        POSE_HINT_MARGIN = b.comment("Plausibility slack (blocks) for a streamed rig.")
                .defineInRange("poseHintMargin", AhfConfig.poseHintMargin(), 0.0, 4.0);
        b.pop();

        b.push("penetration");
        PENETRATION_ENABLED = b.define("penetrationEnabled", AhfConfig.penetrationEnabled());
        PENETRATION_BUDGET = b.comment("Pierce stops once the summed part resistance reaches this.")
                .defineInRange("penetrationBudget", AhfConfig.penetrationBudget(), 0.0, 10.0);
        PENETRATION_ENERGY_FALLOFF = b.defineInRange("penetrationEnergyFalloff",
                AhfConfig.penetrationEnergyFalloff(), 0.0, 1.0);
        PEN_RESIST_HEAD = b.defineInRange("resistHead", AhfConfig.penetrationResistance(HitboxPart.HEAD), 0.0, 10.0);
        PEN_RESIST_TORSO = b.defineInRange("resistTorso", AhfConfig.penetrationResistance(HitboxPart.TORSO), 0.0, 10.0);
        PEN_RESIST_ARM = b.defineInRange("resistArm", AhfConfig.penetrationResistance(HitboxPart.LEFT_ARM), 0.0, 10.0);
        PEN_RESIST_LEG = b.defineInRange("resistLeg", AhfConfig.penetrationResistance(HitboxPart.LEFT_LEG), 0.0, 10.0);
        b.pop();

        b.push("lagCompensation");
        LAG_COMPENSATION = b.comment("false => no history captured; every rewound lookup answers live.")
                .define("enabled", AhfConfig.lagCompensation());
        LAG_COMP_MAX_REWIND_TICKS = b.comment("Hard cap on rewind; the only bound a lying client cannot move.")
                .defineInRange("maxRewindTicks", AhfConfig.lagCompMaxRewindTicks(), 0, 40);
        LAG_COMP_INTERP_TICKS = b.comment("Remote-entity interpolation delay on clients (vanilla lerpTo steps 3).")
                .defineInRange("interpTicks", AhfConfig.lagCompInterpTicks(), 0, 20);
        LAG_COMP_SLACK_TICKS = b.defineInRange("slackTicks", AhfConfig.lagCompSlackTicks(), 0, 20);
        LAG_COMP_NON_PLAYER_RANGE = b.comment("Non-player living entities get history within this many blocks of a",
                        "player; players always.")
                .defineInRange("nonPlayerRange", AhfConfig.lagCompNonPlayerRange(), 0.0, 4096.0);
        RTT_PROBE_INTERVAL_TICKS = b.defineInRange("rttProbeIntervalTicks", AhfConfig.rttProbeIntervalTicks(), 1, 200);
        RTT_WINDOW_SAMPLES = b.comment("RTT = min over this many most recent probe samples.")
                .defineInRange("rttWindowSamples", AhfConfig.rttWindowSamples(), 1, 100);
        b.pop();

        b.push("debug");
        HITBOX_DEBUG = b.comment("Enables live rig tuning deltas.").define("hitboxDebug", AhfConfig.hitboxDebug());
        LOG_HIT_DETECTION = b.comment("Log every classification branch and point-pick ranking.")
                .define("logHitDetection", AhfConfig.logHitDetection());
        b.pop();

        SPEC = b.build();
    }

    private AhfConfigSpec() {
    }

    public static void bind() {
        AhfConfig.setGeometricHitLocation(GEOMETRIC_HIT_LOCATION.get());
        AhfConfig.setPoseAwareArms(POSE_AWARE_ARMS.get());
        AhfConfig.setHeadBandBottom(HEAD_BAND_BOTTOM.get());
        AhfConfig.setLegBandTop(LEG_BAND_TOP.get());
        AhfConfig.setArmSideThreshold(ARM_SIDE_THRESHOLD.get());
        AhfConfig.setMeleeReach(MELEE_REACH.get());

        AhfConfig.setRiggedLimbBoxes(RIGGED_LIMB_BOXES.get());
        AhfConfig.setLimbBoxPadding(LIMB_BOX_PADDING.get());
        AhfConfig.setGunArmPose(GUN_ARM_POSE.get());
        AhfConfig.setAnimatedHitboxes(ANIMATED_HITBOXES.get());

        AhfConfig.setHitRegistrationMode(HIT_REGISTRATION_MODE.get());
        AhfConfig.setHitGapRejectTolerance(HIT_GAP_REJECT_TOLERANCE.get());
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            AhfConfig.setHitEnvelopeReachHorizontal(pose, ENV_REACH_H[pose.ordinal()].get());
            AhfConfig.setHitEnvelopeReachVertical(pose, ENV_REACH_V[pose.ordinal()].get());
        }
        AhfConfig.setStanceEnvelopeReachHorizontal(STANCE_ENV_REACH_H.get());
        AhfConfig.setStanceEnvelopeReachVertical(STANCE_ENV_REACH_V.get());

        AhfConfig.setHitAuthority(HIT_AUTHORITY.get());
        AhfConfig.setPoseStreamMinIntervalTicks(POSE_STREAM_MIN_INTERVAL_TICKS.get());
        AhfConfig.setPoseStreamMaxIntervalTicks(POSE_STREAM_MAX_INTERVAL_TICKS.get());
        AhfConfig.setPoseStreamChangeEpsilon(POSE_STREAM_CHANGE_EPSILON.get());
        AhfConfig.setPoseHintMaxAgeTicks(POSE_HINT_MAX_AGE_TICKS.get());
        AhfConfig.setPoseHintMargin(POSE_HINT_MARGIN.get());

        AhfConfig.setPenetrationEnabled(PENETRATION_ENABLED.get());
        AhfConfig.setPenetrationBudget(PENETRATION_BUDGET.get());
        AhfConfig.setPenetrationEnergyFalloff(PENETRATION_ENERGY_FALLOFF.get());
        AhfConfig.setPenResistHead(PEN_RESIST_HEAD.get());
        AhfConfig.setPenResistTorso(PEN_RESIST_TORSO.get());
        AhfConfig.setPenResistArm(PEN_RESIST_ARM.get());
        AhfConfig.setPenResistLeg(PEN_RESIST_LEG.get());

        AhfConfig.setLagCompensation(LAG_COMPENSATION.get());
        AhfConfig.setLagCompMaxRewindTicks(LAG_COMP_MAX_REWIND_TICKS.get());
        AhfConfig.setLagCompInterpTicks(LAG_COMP_INTERP_TICKS.get());
        AhfConfig.setLagCompSlackTicks(LAG_COMP_SLACK_TICKS.get());
        AhfConfig.setLagCompNonPlayerRange(LAG_COMP_NON_PLAYER_RANGE.get());
        AhfConfig.setRttProbeIntervalTicks(RTT_PROBE_INTERVAL_TICKS.get());
        AhfConfig.setRttWindowSamples(RTT_WINDOW_SAMPLES.get());

        AhfConfig.setHitboxDebug(HITBOX_DEBUG.get());
        AhfConfig.setLogHitDetection(LOG_HIT_DETECTION.get());
    }
}
