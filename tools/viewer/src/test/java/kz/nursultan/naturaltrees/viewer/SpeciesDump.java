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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import kz.nursultan.naturaltrees.treecore.Arms;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.PlacementLimits;
import kz.nursultan.naturaltrees.treecore.TreeDump;
import kz.nursultan.naturaltrees.treecore.TreeGenerator;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.treecore.TrunkParams;

/**
 * Test helper: draws a species file to PNG, at the heights vanilla would give it.
 * {@code ./gradlew :tools:viewer:dumpSpecies -Pspecies=oak -Pseeds=8}
 */
public final class SpeciesDump {

    private SpeciesDump() {
    }

    public static void main(String[] args) throws IOException {
        String species = args[0];
        int seeds = Integer.parseInt(args[1]);
        File dir = new File(args[2]);
        dir.mkdirs();

        Map<String, Object> file = PlacerJson.object(MiniJson.parse(new String(
                ViewerServer.resource("/species/" + species + ".json"), StandardCharsets.UTF_8)), species);
        Map<String, Object> trunkObject = PlacerJson.object(file.get("trunk_placer"), "trunk_placer");
        TrunkParams trunk = PlacerJson.trunk(trunkObject);
        FoliageParams foliage = PlacerJson.foliage(PlacerJson.object(file.get("foliage_placer"), "foliage_placer"));
        int base = PlacerJson.integer(trunkObject, "base_height", null);
        int randA = PlacerJson.integer(trunkObject, "height_rand_a", null);
        int randB = PlacerJson.integer(trunkObject, "height_rand_b", null);

        TreeGenerator generator = new TreeGenerator();
        List<BufferedImage> full = new ArrayList<>();
        List<BufferedImage> bare = new ArrayList<>();
        for (int seed = 1; seed <= seeds; seed++) {
            // The seeds walk the whole height range, lowest first, so a grid always shows both extremes.
            int height = base + (seeds > 1 ? (seed - 1) * (randA + randB) / (seeds - 1) : 0);
            TreeResult r = generator.generate(trunk, foliage, seed, height, PlacementLimits.NONE,
                    (x, y, z) -> y >= 0, (x, y, z) -> y >= 0);
            full.add(eyeLevel(r, true, 12));
            bare.add(eyeLevel(r, false, 12));
            int logs = 0;
            for (int i = 0; i < r.woodCount(); i++) {
                logs += r.isBranch(i) ? 0 : 1;
            }
            int lowestLeaf = Integer.MAX_VALUE, reach = 0;
            for (int i = 0; i < r.leafCount(); i++) {
                lowestLeaf = Math.min(lowestLeaf, r.leafY(i));
                reach = Math.max(reach, Math.max(Math.abs(r.leafX(i)), Math.abs(r.leafZ(i))));
            }
            System.out.printf("%s seed %d height %d: %d logs, %d branches, %d tips, %d leaves (shade -%d, reach -%d, budget -%d),"
                            + " lowest leaf y=%d, widest leaf %d%n",
                    species, seed, height, logs, r.woodCount() - logs, r.tipCount(), r.leafCount(),
                    r.leavesDiscardedByShade(), r.leavesDiscardedByReach(), r.leavesDiscardedByBudget(), lowestLeaf, reach);
        }
        File out = new File(dir, species + "-grid.png");
        ImageIO.write(TreeDump.grid(full, bare), "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    private static final double YAW = Math.toRadians(30), PITCH = Math.toRadians(12);

    /**
     * The tree as a player sees it from the ground some way off: an orthographic view turned 30 degrees and raised 12,
     * so that widths and heights keep their true proportion. {@link TreeDump#isometric} looks down from 27 degrees and
     * draws a block twice as wide as it is tall, which makes every tree look squat and every trunk thick.
     */
    static BufferedImage eyeLevel(TreeResult r, boolean withLeaves, int unit) {
        int n = r.woodCount() + (withLeaves ? r.leafCount() : 0);
        double[][] v = new double[n][];
        for (int i = 0; i < r.woodCount(); i++) {
            v[i] = new double[] {r.woodX(i), r.woodY(i), r.woodZ(i), r.isBranch(i) ? 1 : 0, r.isBranch(i) ? r.woodArms(i) : 0};
        }
        for (int i = 0; withLeaves && i < r.leafCount(); i++) {
            v[r.woodCount() + i] = new double[] {r.leafX(i), r.leafY(i), r.leafZ(i), 2, 0};
        }
        Arrays.sort(v, (a, b) -> Double.compare(depth(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5), depth(a[0] + 0.5, a[1] + 0.5, a[2] + 0.5)));
        double minX = -3, maxX = 3, minY = -1, maxY = 3;
        for (double[] k : v) {
            for (int c = 0; c < 8; c++) {
                double x = k[0] + (c & 1), y = k[1] + (c >> 1 & 1), z = k[2] + (c >> 2 & 1);
                minX = Math.min(minX, sx(x, z)); maxX = Math.max(maxX, sx(x, z));
                minY = Math.min(minY, sy(x, y, z)); maxY = Math.max(maxY, sy(x, y, z));
            }
        }
        int w = (int) Math.ceil((maxX - minX + 2) * unit), h = (int) Math.ceil((maxY - minY + 2) * unit);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xD8E6F0));
        g.fillRect(0, 0, w, h);
        for (double[] k : v) {
            int type = (int) k[3];
            double s = type == 1 ? 0.5 : 1.0, o = (1 - s) / 2, x = k[0] + o, y = k[1] + o, z = k[2] + o;
            Color c = type == 0 ? new Color(0x6B4A2B) : type == 1 ? new Color(0x8A6038) : new Color(0x3F8F3A);
            if (type == 2) {
                // A little variation per block, as leaf textures have, so that masses and holes can be told apart.
                int t = (int) ((((long) k[0] * 73856093L) ^ ((long) k[1] * 19349663L) ^ ((long) k[2] * 83492791L)) & 15) - 8;
                c = new Color(clamp(c.getRed() + t), clamp(c.getGreen() + 2 * t), clamp(c.getBlue() + t));
            }
            box(g, unit, minX, maxY, c, x, y, z, x + s, y + s, z + s);
            // A branch block is a core with an arm toward every neighbour it is joined to (spec 6.3).
            int arms = (int) k[4];
            double a = k[0], b = k[1], d = k[2];
            if ((arms & Arms.DOWN) != 0) { box(g, unit, minX, maxY, c, x, b, z, x + s, y, z + s); }
            if ((arms & Arms.UP) != 0) { box(g, unit, minX, maxY, c, x, y + s, z, x + s, b + 1, z + s); }
            if ((arms & Arms.NORTH) != 0) { box(g, unit, minX, maxY, c, x, y, d, x + s, y + s, z); }
            if ((arms & Arms.SOUTH) != 0) { box(g, unit, minX, maxY, c, x, y, z + s, x + s, y + s, d + 1); }
            if ((arms & Arms.WEST) != 0) { box(g, unit, minX, maxY, c, a, y, z, x, y + s, z + s); }
            if ((arms & Arms.EAST) != 0) { box(g, unit, minX, maxY, c, x + s, y, z, a + 1, y + s, z + s); }
        }
        g.dispose();
        return image;
    }

    /** The three faces of a box that this view shows: top, south and east. */
    private static void box(Graphics2D g, int unit, double minX, double maxY, Color c,
                            double x0, double y0, double z0, double x1, double y1, double z1) {
        quad(g, unit, minX, maxY, c, 1.18, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(g, unit, minX, maxY, c, 0.95, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(g, unit, minX, maxY, c, 0.72, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
    }

    private static int clamp(int c) {
        return Math.max(0, Math.min(255, c));
    }

    private static double sx(double x, double z) {
        return x * Math.cos(YAW) - z * Math.sin(YAW);
    }

    private static double depth(double x, double y, double z) {
        return (x * Math.sin(YAW) + z * Math.cos(YAW)) * Math.cos(PITCH) + y * Math.sin(PITCH);
    }

    private static double sy(double x, double y, double z) {
        return y * Math.cos(PITCH) - (x * Math.sin(YAW) + z * Math.cos(YAW)) * Math.sin(PITCH);
    }

    private static void quad(Graphics2D g, int unit, double minX, double maxY, Color c, double light, double... p) {
        Polygon poly = new Polygon();
        for (int i = 0; i < 12; i += 3) {
            poly.addPoint((int) Math.round((sx(p[i], p[i + 2]) - minX + 1) * unit),
                    (int) Math.round((maxY - sy(p[i], p[i + 1], p[i + 2]) + 1) * unit));
        }
        g.setColor(new Color(clamp((int) (c.getRed() * light)), clamp((int) (c.getGreen() * light)), clamp((int) (c.getBlue() * light))));
        g.fillPolygon(poly);
        g.setColor(new Color(0, 0, 0, 45));
        g.drawPolygon(poly);
    }
}
