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
package kz.nursultan.naturaltrees.felling;

/** What the felling search needs to know about the world; an interface so that the search is tested without the game. */
public interface WoodView {

    int OTHER = 0;
    /** A block in {@code #minecraft:logs} that is not a branch. */
    int LOG = 1;
    int BRANCH = 2;
    /** A leaf block that is not persistent: grown, not placed by a player. */
    int NATURAL_LEAF = 3;

    int kind(int x, int y, int z);

    /** The arm mask of a branch, bit {@code i} for {@code Direction.values()[i]}; 0 for anything else. */
    int arms(int x, int y, int z);
}
