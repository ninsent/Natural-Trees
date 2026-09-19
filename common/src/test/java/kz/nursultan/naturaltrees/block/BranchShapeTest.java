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
package kz.nursultan.naturaltrees.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Spec 6.3: the shape is the union of an 8 px core and one 8 px arm per set direction, for all 64 masks. */
class BranchShapeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyMaskIsCoreAndArms() {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = BranchBlock.shapeOf(mask);
            double[] volume = {0};
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> volume[0] += (x1 - x0) * (y1 - y0) * (z1 - z0));
            double expected = 0.5 * 0.5 * 0.5 + Integer.bitCount(mask) * 0.5 * 0.5 * 0.25;
            assertEquals(expected, volume[0], 1e-9, "volume of mask " + mask);

            AABB bounds = shape.bounds();
            for (Direction direction : Direction.values()) {
                boolean arm = (mask & (1 << direction.ordinal())) != 0;
                double reach = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? bounds.max(direction.getAxis()) : 1.0 - bounds.min(direction.getAxis());
                assertEquals(arm ? 1.0 : 0.75, reach, 1e-9, "mask " + mask + " toward " + direction);
            }
            // Thin in every direction across an arm: nothing wider than the 8 px core.
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> assertTrue(
                    Math.min(x1 - x0, Math.min(y1 - y0, z1 - z0)) <= 0.5 + 1e-9));
        }
    }
}
