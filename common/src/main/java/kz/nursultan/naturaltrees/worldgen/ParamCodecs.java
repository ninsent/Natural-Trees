/*
 * Copyright 2026 Nursultan Akim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kz.nursultan.naturaltrees.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Supplier;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.LevelParams;
import kz.nursultan.naturaltrees.treecore.Shape;
import kz.nursultan.naturaltrees.treecore.StemParams;
import kz.nursultan.naturaltrees.treecore.TrunkParams;

/**
 * Codecs for the generator's parameters (spec 8.2, 9.2). Field names and defaults are the species-file
 * format. Ranges are not repeated here: {@code treecore} checks them when the object is built, and its
 * message, which names the field, becomes the codec error. The viewer reads the same files the same way.
 */
final class ParamCodecs {

    private ParamCodecs() {
    }

    /** Builds a parameter object and turns treecore's range errors into codec errors. */
    private static <T> DataResult<T> checked(Supplier<T> build) {
        try {
            return DataResult.success(build.get());
        } catch (IllegalArgumentException e) {
            final String message = e.getMessage();
            return DataResult.error(() -> message);
        }
    }

    private static final Codec<Shape> SHAPE = Codec.STRING.comapFlatMap(
            name -> checked(() -> Shape.bySerializedName(name)), Shape::serializedName);

    private static MapCodec<Double> number(String name) {
        return Codec.DOUBLE.fieldOf(name);
    }

    private static MapCodec<Double> number(String name, double fallback) {
        return Codec.DOUBLE.optionalFieldOf(name, fallback);
    }

    /** The trunk object: the stem fields and {@code base_splits}. */
    record TrunkStem(StemParams stem, int baseSplits) {
    }

    private record RawStem(double curve, double curveBack, double curveV, int curveRes, double segSplits,
                           double splitAngle, double splitAngleV) {
        StemParams build() {
            return new StemParams(curve, curveBack, curveV, curveRes, segSplits, splitAngle, splitAngleV);
        }

        static RawStem of(StemParams p) {
            return new RawStem(p.curve(), p.curveBack(), p.curveV(), p.curveRes(), p.segSplits(), p.splitAngle(),
                    p.splitAngleV());
        }
    }

    private static final MapCodec<StemParams> STEM = RecordCodecBuilder.<RawStem>mapCodec(i -> i.group(
            number("curve", 0).forGetter(RawStem::curve),
            number("curve_back", 0).forGetter(RawStem::curveBack),
            number("curve_v", 0).forGetter(RawStem::curveV),
            Codec.INT.fieldOf("curve_res").forGetter(RawStem::curveRes),
            number("seg_splits", 0).forGetter(RawStem::segSplits),
            number("split_angle", 0).forGetter(RawStem::splitAngle),
            number("split_angle_v", 0).forGetter(RawStem::splitAngleV)
    ).apply(i, RawStem::new)).flatXmap(raw -> checked(raw::build), p -> DataResult.success(RawStem.of(p)));

    private static final Codec<TrunkStem> TRUNK_STEM = RecordCodecBuilder.create(i -> i.group(
            STEM.forGetter(TrunkStem::stem),
            Codec.INT.optionalFieldOf("base_splits", 0).forGetter(TrunkStem::baseSplits)
    ).apply(i, TrunkStem::new));

    private record RawLevel(StemParams stem, int branches, double length, double lengthV, double downAngle,
                            double downAngleV, double rotate, double rotateV, int tipRadiusOffset) {
        LevelParams build() {
            return new LevelParams(stem, branches, length, lengthV, downAngle, downAngleV, rotate, rotateV, tipRadiusOffset);
        }

        static RawLevel of(LevelParams p) {
            return new RawLevel(p.stem(), p.branches(), p.length(), p.lengthV(), p.downAngle(), p.downAngleV(),
                    p.rotate(), p.rotateV(), p.tipRadiusOffset());
        }
    }

    private static final Codec<LevelParams> LEVEL = RecordCodecBuilder.<RawLevel>mapCodec(i -> i.group(
            STEM.forGetter(RawLevel::stem),
            Codec.INT.fieldOf("branches").forGetter(RawLevel::branches),
            number("length").forGetter(RawLevel::length),
            number("length_v", 0).forGetter(RawLevel::lengthV),
            number("down_angle").forGetter(RawLevel::downAngle),
            number("down_angle_v", 0).forGetter(RawLevel::downAngleV),
            number("rotate").forGetter(RawLevel::rotate),
            number("rotate_v", 0).forGetter(RawLevel::rotateV),
            Codec.INT.optionalFieldOf("tip_radius_offset", 0).forGetter(RawLevel::tipRadiusOffset)
    ).apply(i, RawLevel::new)).flatXmap(raw -> checked(raw::build), p -> DataResult.success(RawLevel.of(p))).codec();

    private record RawTrunk(Shape shape, double baseSize, double attractionUp, double twigRadius, double pipeExponent,
                            int trunkWidthMin, int trunkWidthMax, boolean trunkLeader, int maxRadius,
                            int foliageMargin, int maxTips, TrunkStem trunk, List<LevelParams> levels) {
        TrunkParams build() {
            return new TrunkParams(shape, baseSize, attractionUp, twigRadius, pipeExponent, trunkWidthMin,
                    trunkWidthMax, trunkLeader, maxRadius, foliageMargin, maxTips, trunk.stem(), trunk.baseSplits(), levels);
        }

        static RawTrunk of(TrunkParams p) {
            return new RawTrunk(p.shape(), p.baseSize(), p.attractionUp(), p.twigRadius(), p.pipeExponent(),
                    p.trunkWidthMin(), p.trunkWidthMax(), p.trunkLeader(), p.maxRadius(), p.foliageMargin(),
                    p.maxTips(), new TrunkStem(p.trunk(), p.baseSplits()), p.levels());
        }
    }

    /** The generator's fields of the trunk placer object; the height fields and the provider sit beside them. */
    static final MapCodec<TrunkParams> TRUNK = RecordCodecBuilder.<RawTrunk>mapCodec(i -> i.group(
            SHAPE.fieldOf("shape").forGetter(RawTrunk::shape),
            number("base_size").forGetter(RawTrunk::baseSize),
            number("attraction_up", TrunkParams.DEFAULT_ATTRACTION_UP).forGetter(RawTrunk::attractionUp),
            number("twig_radius", TrunkParams.DEFAULT_TWIG_RADIUS).forGetter(RawTrunk::twigRadius),
            number("pipe_exponent", TrunkParams.DEFAULT_PIPE_EXPONENT).forGetter(RawTrunk::pipeExponent),
            Codec.INT.optionalFieldOf("trunk_width_min", TrunkParams.DEFAULT_TRUNK_WIDTH_MIN).forGetter(RawTrunk::trunkWidthMin),
            Codec.INT.optionalFieldOf("trunk_width_max", TrunkParams.DEFAULT_TRUNK_WIDTH_MAX).forGetter(RawTrunk::trunkWidthMax),
            Codec.BOOL.optionalFieldOf("trunk_leader", TrunkParams.DEFAULT_TRUNK_LEADER).forGetter(RawTrunk::trunkLeader),
            Codec.INT.fieldOf("max_radius").forGetter(RawTrunk::maxRadius),
            Codec.INT.optionalFieldOf("foliage_margin", TrunkParams.DEFAULT_FOLIAGE_MARGIN).forGetter(RawTrunk::foliageMargin),
            Codec.INT.fieldOf("max_tips").forGetter(RawTrunk::maxTips),
            TRUNK_STEM.fieldOf("trunk").forGetter(RawTrunk::trunk),
            LEVEL.listOf().fieldOf("levels").forGetter(RawTrunk::levels)
    ).apply(i, RawTrunk::new)).flatXmap(raw -> checked(raw::build), p -> DataResult.success(RawTrunk.of(p)));

    private record RawFoliage(double foliageStart, double trunkFoliage, double radiusBase, double radiusTip,
                              double radiusV, double flatten, double lift, double density, int smother,
                              int maxDistance, int maxLeaves) {
        FoliageParams build() {
            return new FoliageParams(foliageStart, trunkFoliage, radiusBase, radiusTip, radiusV, flatten, lift,
                    density, smother, maxDistance, maxLeaves);
        }

        static RawFoliage of(FoliageParams p) {
            return new RawFoliage(p.foliageStart(), p.trunkFoliage(), p.radiusBase(), p.radiusTip(), p.radiusV(),
                    p.flatten(), p.lift(), p.density(), p.smother(), p.maxDistance(), p.maxLeaves());
        }
    }

    static final MapCodec<FoliageParams> FOLIAGE = RecordCodecBuilder.<RawFoliage>mapCodec(i -> i.group(
            number("foliage_start", FoliageParams.DEFAULT_FOLIAGE_START).forGetter(RawFoliage::foliageStart),
            number("trunk_foliage", FoliageParams.DEFAULT_TRUNK_FOLIAGE).forGetter(RawFoliage::trunkFoliage),
            number("radius_base", FoliageParams.DEFAULT_RADIUS_BASE).forGetter(RawFoliage::radiusBase),
            number("radius_tip", FoliageParams.DEFAULT_RADIUS_TIP).forGetter(RawFoliage::radiusTip),
            number("radius_v", FoliageParams.DEFAULT_RADIUS_V).forGetter(RawFoliage::radiusV),
            number("flatten", FoliageParams.DEFAULT_FLATTEN).forGetter(RawFoliage::flatten),
            number("lift", FoliageParams.DEFAULT_LIFT).forGetter(RawFoliage::lift),
            number("density", FoliageParams.DEFAULT_DENSITY).forGetter(RawFoliage::density),
            Codec.INT.optionalFieldOf("smother", FoliageParams.DEFAULT_SMOTHER).forGetter(RawFoliage::smother),
            Codec.INT.optionalFieldOf("max_distance", FoliageParams.DEFAULT_MAX_DISTANCE).forGetter(RawFoliage::maxDistance),
            Codec.INT.fieldOf("max_leaves").forGetter(RawFoliage::maxLeaves)
    ).apply(i, RawFoliage::new)).flatXmap(raw -> checked(raw::build), p -> DataResult.success(RawFoliage.of(p)));
}
