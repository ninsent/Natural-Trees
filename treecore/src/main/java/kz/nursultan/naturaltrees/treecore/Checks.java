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

/** Range checks for parameters. Every message starts with the species-file name of the field. */
final class Checks {

    private Checks() {
    }

    static int range(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + ": " + value + " is outside " + min + " to " + max);
        }
        return value;
    }

    /** Checks the range, then returns the value as the game's codecs deliver it: rounded to a float. */
    static double range(String field, double value, double min, double max) {
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(field + ": " + value + " is outside " + min + " to " + max);
        }
        return (double) (float) value;
    }

    /** A float field without a range: it must be finite. Returned rounded to a float, as {@link #range}. */
    static double finite(String field, double value) {
        final double rounded = (double) (float) value;
        if (Double.isNaN(rounded) || Double.isInfinite(rounded)) {
            throw new IllegalArgumentException(field + ": " + value + " is not a finite number");
        }
        return rounded;
    }

    static <T> T notNull(String field, T value) {
        if (value == null) {
            throw new IllegalArgumentException(field + ": missing");
        }
        return value;
    }
}
