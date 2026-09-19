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

/**
 * The {@code roots} object of the trunk placer: surface roots that leave the lower trunk, arch outward and go into
 * the ground or the water. A root is wood only: it has no tip, bears no foliage and carries no load, so a species
 * without roots generates exactly as it did before they existed.
 *
 * @param count roots per tree, 0 to 8; 0 turns them off
 * @param height how far up the trunk the highest root leaves it, in blocks
 * @param spread horizontal distance from the trunk at which a root reaches the level of the trunk's base
 * @param depth how many blocks a root continues below that level, where the ground falls away or is water
 * @param logShare share of a root's length, from the trunk, that is 1×1 logs; the rest is branch blocks
 */
public record RootParams(int count, double height, double spread, int depth, double logShare) {

    public static final double DEFAULT_HEIGHT = 3.0;
    public static final double DEFAULT_SPREAD = 3.0;
    public static final int DEFAULT_DEPTH = 2;
    public static final double DEFAULT_LOG_SHARE = 0.6;

    /** No roots: the value of a species file without a {@code roots} object. */
    public static final RootParams NONE = new RootParams(0, DEFAULT_HEIGHT, DEFAULT_SPREAD, DEFAULT_DEPTH, DEFAULT_LOG_SHARE);

    public RootParams {
        Checks.range("roots.count", count, 0, 8);
        height = Checks.range("roots.height", height, 1.0, 8.0);
        spread = Checks.range("roots.spread", spread, 1.0, 8.0);
        Checks.range("roots.depth", depth, 0, 6);
        logShare = Checks.range("roots.log_share", logShare, 0.0, 1.0);
    }
}
