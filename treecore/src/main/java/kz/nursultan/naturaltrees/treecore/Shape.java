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
 * Crown envelope: the shapes 0 to 7 of the paper's {@code ShapeRatio} function (Weber and Penn, section 4.3).
 * Shape 8, the pruning envelope, is not used (spec 7.1).
 */
public enum Shape {
    CONICAL,
    SPHERICAL,
    HEMISPHERICAL,
    CYLINDRICAL,
    TAPERED_CYLINDRICAL,
    FLAME,
    INVERSE_CONICAL,
    TEND_FLAME;

    /** The paper's {@code ShapeRatio(shape, ratio)} for a ratio in [0, 1]; values outside are clamped. */
    public double ratio(double ratio) {
        final double r = ratio < 0.0 ? 0.0 : (ratio > 1.0 ? 1.0 : ratio);
        switch (this) {
            case CONICAL:
                return 0.2 + 0.8 * r;
            case SPHERICAL:
                return 0.2 + 0.8 * StrictMath.sin(StrictMath.PI * r);
            case HEMISPHERICAL:
                return 0.2 + 0.8 * StrictMath.sin(0.5 * StrictMath.PI * r);
            case CYLINDRICAL:
                return 1.0;
            case TAPERED_CYLINDRICAL:
                return 0.5 + 0.5 * r;
            case FLAME:
                return r <= 0.7 ? r / 0.7 : (1.0 - r) / 0.3;
            case INVERSE_CONICAL:
                return 1.0 - 0.8 * r;
            case TEND_FLAME:
                return r <= 0.7 ? 0.5 + 0.5 * r / 0.7 : 0.5 + 0.5 * (1.0 - r) / 0.3;
            default:
                throw new AssertionError(this);
        }
    }

    /** The name used in species files, for example {@code tapered_cylindrical}. */
    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The shape with the given species-file name. */
    public static Shape bySerializedName(String name) {
        for (Shape shape : values()) {
            if (shape.serializedName().equals(name)) {
                return shape;
            }
        }
        throw new IllegalArgumentException("shape: unknown shape '" + name + "'");
    }
}
