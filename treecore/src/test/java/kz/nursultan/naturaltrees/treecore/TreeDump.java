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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Test helper, not a feature: draws generated trees to PNG files (isometric view, side and top
 * projections) or to text slices, so that the output can be inspected without the game.
 *
 * <p>Run with {@code ./gradlew :treecore:dumpTrees -Pspecies=oak -Pseeds=8 -Pheight=9}; the files land in
 * {@code treecore/build/tree-dumps}.
 */
public final class TreeDump {

    private static final Color LOG = new Color(0x6B4A2B);
    private static final Color BRANCH = new Color(0x9A7044);
    private static final Color LEAF = new Color(0x3F8F3A);
    private static final Color TIP = new Color(0xD03030);
    private static final int UNIT = 12;

    private TreeDump() {
    }

    /** One voxel, copied out of a result so that several trees can be drawn together. */
    private record Voxel(int x, int y, int z, int type, int value) {
        static final int T_LOG = 0;
        static final int T_BRANCH = 1;
        static final int T_LEAF = 2;
    }

    private static List<Voxel> voxels(TreeResult r, boolean withLeaves) {
        List<Voxel> out = new ArrayList<>();
        for (int i = 0; i < r.woodCount(); i++) {
            out.add(new Voxel(r.woodX(i), r.woodY(i), r.woodZ(i), r.isBranch(i) ? Voxel.T_BRANCH : Voxel.T_LOG,
                    r.woodLoad(i)));
        }
        if (withLeaves) {
            for (int i = 0; i < r.leafCount(); i++) {
                out.add(new Voxel(r.leafX(i), r.leafY(i), r.leafZ(i), Voxel.T_LEAF, r.leafDistance(i)));
            }
        }
        return out;
    }

    /** An isometric drawing seen from the +x, +z side and above. Branch blocks are drawn as small cubes. */
    public static BufferedImage isometric(TreeResult r, boolean withLeaves, int unit) {
        List<Voxel> all = voxels(r, withLeaves);
        all.sort(Comparator.comparingInt((Voxel v) -> v.x + v.y + v.z).thenComparingInt(v -> v.y));
        int span = 2 * VoxelMap.RADIUS + 2;
        int width = span * 2 * unit;
        int topY = 8;
        for (Voxel v : all) {
            topY = Math.max(topY, v.y + 2);
        }
        int height = (span + topY * 2 + 4) * unit;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xD8E6F0));
        g.fillRect(0, 0, width, height);
        int originX = width / 2;
        int originY = height - (span / 2 + 3) * unit;
        for (Voxel v : all) {
            Color base = v.type == Voxel.T_LOG ? LOG : v.type == Voxel.T_BRANCH ? BRANCH : LEAF;
            double size = v.type == Voxel.T_BRANCH ? 0.5 : 1.0;
            double inset = (1.0 - size) / 2.0;
            cube(g, originX, originY, unit, v.x + inset, v.y + inset, v.z + inset, size, base);
        }
        g.dispose();

        // Crop to the tree, with a margin.
        int minSx = Integer.MAX_VALUE, maxSx = Integer.MIN_VALUE, minSy = Integer.MAX_VALUE, maxSy = Integer.MIN_VALUE;
        for (Voxel v : all) {
            int sx = originX + (v.x - v.z) * unit;
            int sy = originY + (v.x + v.z) * unit / 2 - v.y * unit;
            minSx = Math.min(minSx, sx - 2 * unit);
            maxSx = Math.max(maxSx, sx + 2 * unit);
            minSy = Math.min(minSy, sy - 2 * unit);
            maxSy = Math.max(maxSy, sy + 2 * unit);
        }
        if (all.isEmpty()) {
            return image;
        }
        minSx = Math.max(0, minSx);
        minSy = Math.max(0, minSy);
        maxSx = Math.min(width, maxSx);
        maxSy = Math.min(height, maxSy);
        return image.getSubimage(minSx, minSy, maxSx - minSx, maxSy - minSy);
    }

    private static void cube(Graphics2D g, int ox, int oy, int unit, double x, double y, double z, double s, Color c) {
        // Corners of the three visible faces: top, the +x side and the +z side.
        double[][] top = {{x, y + s, z}, {x + s, y + s, z}, {x + s, y + s, z + s}, {x, y + s, z + s}};
        double[][] east = {{x + s, y, z}, {x + s, y + s, z}, {x + s, y + s, z + s}, {x + s, y, z + s}};
        double[][] south = {{x, y, z + s}, {x + s, y, z + s}, {x + s, y + s, z + s}, {x, y + s, z + s}};
        face(g, ox, oy, unit, top, c.brighter());
        face(g, ox, oy, unit, east, c);
        face(g, ox, oy, unit, south, c.darker());
    }

    private static void face(Graphics2D g, int ox, int oy, int unit, double[][] corners, Color c) {
        Polygon p = new Polygon();
        for (double[] k : corners) {
            p.addPoint(ox + (int) Math.round((k[0] - k[2]) * unit),
                    oy + (int) Math.round((k[0] + k[2]) * unit / 2.0 - k[1] * unit));
        }
        g.setColor(c);
        g.fillPolygon(p);
        g.setColor(new Color(0, 0, 0, 60));
        g.drawPolygon(p);
    }

    /** Side (x, y), front (z, y) and top (x, z) projections next to each other; tips are red. */
    public static BufferedImage projections(TreeResult r, int unit) {
        int span = 2 * VoxelMap.RADIUS + 1;
        int topY = 8;
        for (int i = 0; i < r.woodCount(); i++) {
            topY = Math.max(topY, r.woodY(i) + 1);
        }
        for (int i = 0; i < r.leafCount(); i++) {
            topY = Math.max(topY, r.leafY(i) + 1);
        }
        int rows = Math.max(topY + 2, span);
        BufferedImage image = new BufferedImage(span * 3 * unit + 4 * unit, rows * unit, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int pass = 0; pass < 3; pass++) {
            for (Voxel v : voxels(r, true)) {
                if ((pass == 0) != (v.type == Voxel.T_LEAF)) {
                    if (pass == 0 || v.type == Voxel.T_LEAF || pass == 2) {
                        continue;
                    }
                }
                if (pass == 2) {
                    continue;
                }
                g.setColor(v.type == Voxel.T_LOG ? LOG : v.type == Voxel.T_BRANCH ? BRANCH : LEAF);
                plot(g, unit, span, rows, v.x, v.y, v.z);
            }
        }
        g.setColor(TIP);
        for (int i = 0; i < r.tipCount(); i++) {
            plot(g, unit, span, rows, r.tipX(i), r.tipY(i), r.tipZ(i));
        }
        g.dispose();
        return image;
    }

    private static void plot(Graphics2D g, int unit, int span, int rows, int x, int y, int z) {
        int cx = x + VoxelMap.RADIUS;
        int cz = z + VoxelMap.RADIUS;
        g.fillRect(cx * unit, (rows - 2 - y) * unit, unit, unit);
        g.fillRect((span + 2 + cz) * unit, (rows - 2 - y) * unit, unit, unit);
        g.fillRect((2 * span + 4 + cx) * unit, cz * unit, unit, unit);
    }

    /** Horizontal slices as text, top layer first: {@code #} log, {@code +} branch, {@code o} leaf. */
    public static String slices(TreeResult r) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        List<Voxel> all = voxels(r, true);
        for (Voxel v : all) {
            minY = Math.min(minY, v.y);
            maxY = Math.max(maxY, v.y);
        }
        StringBuilder out = new StringBuilder();
        int span = 2 * VoxelMap.RADIUS + 1;
        for (int y = maxY; y >= minY; y--) {
            char[][] grid = new char[span][span];
            for (char[] row : grid) {
                java.util.Arrays.fill(row, '.');
            }
            for (Voxel v : all) {
                if (v.y == y) {
                    grid[v.z + VoxelMap.RADIUS][v.x + VoxelMap.RADIUS] =
                            v.type == Voxel.T_LOG ? '#' : v.type == Voxel.T_BRANCH ? '+' : 'o';
                }
            }
            out.append("y=").append(y).append('\n');
            for (char[] row : grid) {
                out.append(row).append('\n');
            }
        }
        return out.toString();
    }

    /** Several trees side by side: each column is one seed, with and without leaves. */
    public static BufferedImage grid(List<BufferedImage> withLeaves, List<BufferedImage> woodOnly) {
        int w = 0, h = 0;
        for (BufferedImage i : withLeaves) {
            w = Math.max(w, i.getWidth());
            h = Math.max(h, i.getHeight());
        }
        BufferedImage out = new BufferedImage(w * withLeaves.size(), h * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0xD8E6F0));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int i = 0; i < withLeaves.size(); i++) {
            g.drawImage(withLeaves.get(i), i * w + (w - withLeaves.get(i).getWidth()) / 2, h - withLeaves.get(i).getHeight(), null);
            g.drawImage(woodOnly.get(i), i * w + (w - woodOnly.get(i).getWidth()) / 2, 2 * h - woodOnly.get(i).getHeight(), null);
        }
        g.dispose();
        return out;
    }

    public static void main(String[] args) throws IOException {
        String species = args.length > 0 ? args[0] : "oak";
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 6;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 9;
        File dir = new File(args.length > 3 ? args[3] : "build/tree-dumps");
        dir.mkdirs();

        TrunkParams trunk;
        FoliageParams foliage;
        switch (species) {
            case "oak" -> {
                trunk = TestSpecies.oak();
                foliage = TestSpecies.oakFoliage();
            }
            case "large" -> {
                trunk = TestSpecies.largeBuilder().build();
                foliage = TestSpecies.largeFoliage();
            }
            default -> throw new IllegalArgumentException("unknown species " + species);
        }

        TreeGenerator generator = new TreeGenerator();
        List<BufferedImage> full = new ArrayList<>();
        List<BufferedImage> bare = new ArrayList<>();
        for (int seed = 1; seed <= seeds; seed++) {
            TreeResult r = generator.generate(trunk, foliage, seed, height, PlacementLimits.NONE,
                    (x, y, z) -> y >= 0, (x, y, z) -> y >= 0);
            full.add(isometric(r, true, UNIT));
            bare.add(isometric(r, false, UNIT));
            int logs = 0;
            for (int i = 0; i < r.woodCount(); i++) {
                logs += r.isBranch(i) ? 0 : 1;
            }
            System.out.printf("%s seed %d: %d logs, %d branches, %d tips, %d leaves (shade -%d, reach -%d, budget -%d),"
                            + " stems %d (budget -%d, truncated %d, face -%d)%n",
                    species, seed, logs, r.woodCount() - logs, r.tipCount(), r.leafCount(),
                    r.leavesDiscardedByShade(), r.leavesDiscardedByReach(), r.leavesDiscardedByBudget(),
                    r.stemsGenerated(), r.stemsRemovedByTipBudget(), r.stemsTruncated(), r.stemsDroppedByFaceRule());
            if (seed == 1) {
                ImageIO.write(projections(r, 6), "png", new File(dir, species + "-projections.png"));
            }
        }
        File out = new File(dir, species + "-grid.png");
        ImageIO.write(grid(full, bare), "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }
}
