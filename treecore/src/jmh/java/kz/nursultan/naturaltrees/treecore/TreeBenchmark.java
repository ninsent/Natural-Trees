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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Spec section 15: a medium tree, wood and foliage together, in under 1 ms. Run with
 * {@code ./gradlew :treecore:jmh}; the GC profiler's {@code gc.alloc.rate.norm} shows the bytes allocated
 * per tree, which spec 7.8 wants at zero once the buffers have grown.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class TreeBenchmark {

    private static final WorldRead ABOVE_GROUND = (x, y, z) -> y >= 0;

    @Param({"small", "medium", "large"})
    public String size;

    private final TreeGenerator generator = new TreeGenerator();
    private TrunkParams trunk;
    private FoliageParams foliage;
    private int height;
    private long seed;

    @Setup
    public void setup() {
        switch (size) {
            case "small" -> {
                trunk = species(Shape.HEMISPHERICAL, 0.4, 5, 3, 8, 3, 5, 0.55, 2, 0.5);
                foliage = FoliageParams.defaults(250);
                height = 9;
            }
            case "medium" -> {
                trunk = species(Shape.SPHERICAL, 0.3, 8, 4, 14, 4, 6, 0.55, 3, 0.45);
                foliage = FoliageParams.defaults(600);
                height = 13;
            }
            default -> {
                trunk = species(Shape.HEMISPHERICAL, 0.3, 12, 4, 28, 4, 12, 0.5, 4, 0.45);
                foliage = FoliageParams.defaults(1200);
                height = 20;
            }
        }
    }

    private static TrunkParams species(Shape shape, double baseSize, int maxRadius, int margin, int maxTips,
                                       int curveRes, int limbs, double limbLength, int twigs, double twigLength) {
        return TrunkParams.builder().shape(shape).baseSize(baseSize).attractionUp(0.4).maxRadius(maxRadius)
                .foliageMargin(margin).maxTips(maxTips).trunk(new StemParams(0, 0, 15, curveRes, 0, 0, 0))
                .levels(new LevelParams(new StemParams(-25, 0, 40, curveRes, 0.3, 35, 10), limbs, limbLength, 0.1,
                                60, -30, 140, 20, 0),
                        new LevelParams(new StemParams(0, 0, 30, 2, 0, 0, 0), twigs, twigLength, 0.1, 45, 10, 140, 30, 0))
                .build();
    }

    @Benchmark
    public int generate() {
        TreeResult r = generator.generate(trunk, foliage, seed++, height, PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
        return r.woodCount() + r.leafCount();
    }
}
