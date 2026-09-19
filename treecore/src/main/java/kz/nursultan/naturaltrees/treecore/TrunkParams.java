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
package kz.nursultan.naturaltrees.treecore;

import java.util.List;

/**
 * The generator's share of the trunk placer's parameters (spec 8.2). The height fields and the branch
 * provider belong to the Minecraft side; the generator receives the height as a number.
 */
public record TrunkParams(Shape shape, double baseSize, double attractionUp, double twigRadius,
                          double pipeExponent, int trunkWidthMin, int trunkWidthMax, boolean trunkLeader,
                          int maxRadius, int foliageMargin, int maxTips, StemParams trunk, int baseSplits,
                          List<LevelParams> levels) {

    public static final double DEFAULT_ATTRACTION_UP = 0.0;
    public static final double DEFAULT_TWIG_RADIUS = 0.25;
    public static final double DEFAULT_PIPE_EXPONENT = 2.0;
    public static final int DEFAULT_TRUNK_WIDTH_MIN = 1;
    public static final int DEFAULT_TRUNK_WIDTH_MAX = 2;
    public static final boolean DEFAULT_TRUNK_LEADER = false;
    public static final int DEFAULT_FOLIAGE_MARGIN = 3;

    /** Widest reach of a tree from its trunk origin, in blocks: wood plus foliage margin (spec 8.2, 13). */
    public static final int MAX_REACH = 16;

    public TrunkParams {
        Checks.notNull("shape", shape);
        baseSize = Checks.range("base_size", baseSize, 0.0, 0.9);
        attractionUp = Checks.range("attraction_up", attractionUp, -3.0, 3.0);
        twigRadius = Checks.range("twig_radius", twigRadius, 0.1, 0.5);
        pipeExponent = Checks.range("pipe_exponent", pipeExponent, 1.5, 3.5);
        Checks.range("trunk_width_min", trunkWidthMin, 1, 2);
        Checks.range("trunk_width_max", trunkWidthMax, 1, 2);
        if (trunkWidthMin > trunkWidthMax) {
            throw new IllegalArgumentException("trunk_width_min: " + trunkWidthMin
                    + " is greater than trunk_width_max " + trunkWidthMax);
        }
        Checks.range("max_radius", maxRadius, 2, 14);
        Checks.range("foliage_margin", foliageMargin, 1, 6);
        if (maxRadius + foliageMargin > MAX_REACH) {
            throw new IllegalArgumentException("max_radius + foliage_margin: " + maxRadius + " + " + foliageMargin
                    + " is greater than " + MAX_REACH);
        }
        Checks.range("max_tips", maxTips, 1, 48);
        Checks.notNull("trunk", trunk);
        Checks.range("base_splits", baseSplits, 0, 3);
        Checks.notNull("levels", levels);
        if (levels.isEmpty() || levels.size() > 3) {
            throw new IllegalArgumentException("levels: " + levels.size() + " entries, expected 1 to 3");
        }
        levels = List.copyOf(levels);
    }

    /** A builder preset with the defaults of spec 8.2. Required fields have no default and must be set. */
    public static Builder builder() {
        return new Builder();
    }

    /** A builder preset with this object's values. */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.shape = shape;
        b.baseSize = baseSize;
        b.attractionUp = attractionUp;
        b.twigRadius = twigRadius;
        b.pipeExponent = pipeExponent;
        b.trunkWidthMin = trunkWidthMin;
        b.trunkWidthMax = trunkWidthMax;
        b.trunkLeader = trunkLeader;
        b.maxRadius = maxRadius;
        b.foliageMargin = foliageMargin;
        b.maxTips = maxTips;
        b.trunk = trunk;
        b.baseSplits = baseSplits;
        b.levels = levels;
        return b;
    }

    public static final class Builder {
        private Shape shape;
        private Double baseSize;
        private double attractionUp = DEFAULT_ATTRACTION_UP;
        private double twigRadius = DEFAULT_TWIG_RADIUS;
        private double pipeExponent = DEFAULT_PIPE_EXPONENT;
        private int trunkWidthMin = DEFAULT_TRUNK_WIDTH_MIN;
        private int trunkWidthMax = DEFAULT_TRUNK_WIDTH_MAX;
        private boolean trunkLeader = DEFAULT_TRUNK_LEADER;
        private Integer maxRadius;
        private int foliageMargin = DEFAULT_FOLIAGE_MARGIN;
        private Integer maxTips;
        private StemParams trunk;
        private int baseSplits;
        private List<LevelParams> levels;

        private Builder() {
        }

        public Builder shape(Shape v) { shape = v; return this; }
        public Builder baseSize(double v) { baseSize = v; return this; }
        public Builder attractionUp(double v) { attractionUp = v; return this; }
        public Builder twigRadius(double v) { twigRadius = v; return this; }
        public Builder pipeExponent(double v) { pipeExponent = v; return this; }
        public Builder trunkWidth(int min, int max) { trunkWidthMin = min; trunkWidthMax = max; return this; }
        public Builder trunkLeader(boolean v) { trunkLeader = v; return this; }
        public Builder maxRadius(int v) { maxRadius = v; return this; }
        public Builder foliageMargin(int v) { foliageMargin = v; return this; }
        public Builder maxTips(int v) { maxTips = v; return this; }
        public Builder trunk(StemParams v) { trunk = v; return this; }
        public Builder baseSplits(int v) { baseSplits = v; return this; }
        public Builder levels(List<LevelParams> v) { levels = v; return this; }
        public Builder levels(LevelParams... v) { levels = List.of(v); return this; }

        public TrunkParams build() {
            return new TrunkParams(shape, Checks.notNull("base_size", baseSize), attractionUp, twigRadius,
                    pipeExponent, trunkWidthMin, trunkWidthMax, trunkLeader, Checks.notNull("max_radius", maxRadius),
                    foliageMargin, Checks.notNull("max_tips", maxTips), trunk, baseSplits, levels);
        }
    }
}
