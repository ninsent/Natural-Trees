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
 * The one test of spec 7.5 that reads the world: whether a tree block may replace what is at a position.
 * Coordinates are relative to the trunk origin. The generator calls it only for positions that passed
 * every arithmetic test, and at most once per position and tree.
 */
@FunctionalInterface
public interface WorldRead {

    WorldRead ALWAYS = (x, y, z) -> true;

    boolean test(int x, int y, int z);
}
