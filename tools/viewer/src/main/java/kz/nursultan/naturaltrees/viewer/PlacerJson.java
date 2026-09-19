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
package kz.nursultan.naturaltrees.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.LevelParams;
import kz.nursultan.naturaltrees.treecore.Shape;
import kz.nursultan.naturaltrees.treecore.StemParams;
import kz.nursultan.naturaltrees.treecore.TrunkParams;

/**
 * Reads the {@code trunk_placer} and {@code foliage_placer} objects of a species file (spec 8.2, 9.2) into
 * the generator's parameters, with the same defaults the game's codecs will apply. Fields that belong to
 * the Minecraft side ({@code type}, the height fields, {@code branch_provider}) are ignored here.
 */
final class PlacerJson {

    private PlacerJson() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(field + ": expected an object");
        }
        return (Map<String, Object>) value;
    }

    static double number(Map<String, Object> o, String field, Double fallback) {
        Object v = o.get(field);
        if (v == null) {
            if (fallback == null) {
                throw new IllegalArgumentException(field + ": missing");
            }
            return fallback;
        }
        if (!(v instanceof Double)) {
            throw new IllegalArgumentException(field + ": expected a number");
        }
        return (Double) v;
    }

    static int integer(Map<String, Object> o, String field, Integer fallback) {
        double v = number(o, field, fallback == null ? null : (double) fallback);
        if (v != Math.rint(v)) {
            throw new IllegalArgumentException(field + ": expected a whole number, was " + v);
        }
        return (int) v;
    }

    private static StemParams stem(Map<String, Object> o) {
        return new StemParams(number(o, "curve", 0.0), number(o, "curve_back", 0.0), number(o, "curve_v", 0.0),
                integer(o, "curve_res", null), number(o, "seg_splits", 0.0), number(o, "split_angle", 0.0),
                number(o, "split_angle_v", 0.0));
    }

    static TrunkParams trunk(Map<String, Object> o) {
        Map<String, Object> trunk = object(o.get("trunk"), "trunk");
        Object levelsValue = o.get("levels");
        if (!(levelsValue instanceof List)) {
            throw new IllegalArgumentException("levels: expected a list");
        }
        List<LevelParams> levels = new ArrayList<>();
        for (Object item : (List<?>) levelsValue) {
            Map<String, Object> l = object(item, "levels");
            levels.add(new LevelParams(stem(l), integer(l, "branches", null), number(l, "length", null),
                    number(l, "length_v", 0.0), number(l, "down_angle", null), number(l, "down_angle_v", 0.0),
                    number(l, "rotate", null), number(l, "rotate_v", 0.0), integer(l, "tip_radius_offset", 0)));
        }
        Object shape = o.get("shape");
        if (!(shape instanceof String)) {
            throw new IllegalArgumentException("shape: missing");
        }
        boolean leader = Boolean.TRUE.equals(o.get("trunk_leader"));
        return TrunkParams.builder()
                .shape(Shape.bySerializedName((String) shape))
                .baseSize(number(o, "base_size", null))
                .attractionUp(number(o, "attraction_up", TrunkParams.DEFAULT_ATTRACTION_UP))
                .twigRadius(number(o, "twig_radius", TrunkParams.DEFAULT_TWIG_RADIUS))
                .pipeExponent(number(o, "pipe_exponent", TrunkParams.DEFAULT_PIPE_EXPONENT))
                .trunkWidth(integer(o, "trunk_width_min", TrunkParams.DEFAULT_TRUNK_WIDTH_MIN),
                        integer(o, "trunk_width_max", TrunkParams.DEFAULT_TRUNK_WIDTH_MAX))
                .trunkLeader(leader)
                .maxRadius(integer(o, "max_radius", null))
                .foliageMargin(integer(o, "foliage_margin", TrunkParams.DEFAULT_FOLIAGE_MARGIN))
                .maxTips(integer(o, "max_tips", null))
                .trunk(stem(trunk))
                .baseSplits(integer(trunk, "base_splits", 0))
                .levels(levels)
                .build();
    }

    static FoliageParams foliage(Map<String, Object> o) {
        return new FoliageParams(
                number(o, "foliage_start", FoliageParams.DEFAULT_FOLIAGE_START),
                number(o, "trunk_foliage", FoliageParams.DEFAULT_TRUNK_FOLIAGE),
                number(o, "radius_base", FoliageParams.DEFAULT_RADIUS_BASE),
                number(o, "radius_tip", FoliageParams.DEFAULT_RADIUS_TIP),
                number(o, "radius_v", FoliageParams.DEFAULT_RADIUS_V),
                number(o, "flatten", FoliageParams.DEFAULT_FLATTEN),
                number(o, "lift", FoliageParams.DEFAULT_LIFT),
                number(o, "density", FoliageParams.DEFAULT_DENSITY),
                integer(o, "smother", FoliageParams.DEFAULT_SMOTHER),
                integer(o, "max_distance", FoliageParams.DEFAULT_MAX_DISTANCE),
                integer(o, "max_leaves", null));
    }
}
