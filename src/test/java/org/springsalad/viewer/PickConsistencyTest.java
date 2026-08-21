package org.springsalad.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Picking is the check that rotation alone cannot give you.
 *
 * <p>A viewer can turn the scene perfectly while its screen-to-world mapping is inconsistent —
 * the picture looks right and only clicks are wrong, which no screenshot review catches. These
 * tests close the loop in both directions: render the scene, find where a site actually landed in
 * pixels, ask the canvas what is under that point, and require the same site back. If the forward
 * projection and the inverse ever disagree — a handedness mistake, a sign in the y-flip, a drift
 * between paint and pick — this fails.
 */
class PickConsistencyTest {

    private static final int W = 360, H = 360;

    /** Sites far enough apart to be told apart by colour, at differing depths. */
    private static SpringSaladViewerCanvas canvas() {
        List<SpringSaladTrajectory.Site> sites = new ArrayList<>(List.of(
                new SpringSaladTrajectory.Site(10, 3.0, "RED", -18, -10, 12),
                new SpringSaladTrajectory.Site(20, 3.0, "BLUE", 18, -10, -12),
                new SpringSaladTrajectory.Site(30, 3.0, "LIME", 0, 16, 0)));
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        c.setTrajectory(new SpringSaladTrajectory(1e-3, 1e-4, 50, 50, 10, 90,
                List.of(new SpringSaladTrajectory.Frame(0, 0, sites, new ArrayList<>()))));
        c.setSize(W, H);
        c.setShowBox(false);
        c.setShowMembrane(false);
        c.setShowLinks(false);
        return c;
    }

    /** Centroid of the pixels drawn in each site's colour, keyed by site id. */
    private static Map<Integer, int[]> renderedCentroids(SpringSaladViewerCanvas c) {
        BufferedImage img = c.renderToImage(W, H);
        Map<Integer, long[]> acc = new LinkedHashMap<>();
        for (SpringSaladTrajectory.Site s : c.getTrajectory().getFrames().get(0).getSites()) {
            acc.put(s.getId(), new long[3]);
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = img.getRGB(x, y);
                for (SpringSaladTrajectory.Site s : c.getTrajectory().getFrames().get(0).getSites()) {
                    if (isShadeOf(rgb, SpringSaladViewerCanvas.colorForName(s.getColor()))) {
                        long[] a = acc.get(s.getId());
                        a[0] += x; a[1] += y; a[2]++;
                    }
                }
            }
        }
        Map<Integer, int[]> out = new LinkedHashMap<>();
        acc.forEach((id, a) -> {
            if (a[2] > 0) {
                out.put(id, new int[]{(int) (a[0] / a[2]), (int) (a[1] / a[2])});
            }
        });
        return out;
    }

    /**
     * Sprites are depth-shaded, so match on which channel dominates rather than an exact RGB.
     * The three site colours are pure red / green / blue precisely so this cannot be ambiguous —
     * an earlier version used GOLD, which is red-dominant and silently merged with RED.
     */
    private static boolean isShadeOf(int rgb, Color base) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        if (r + g + b < 40) {
            return false; // background
        }
        return dominant(r, g, b) == dominant(base.getRed(), base.getGreen(), base.getBlue());
    }

    private static int dominant(int r, int g, int b) {
        if (r > g && r > b) return 0;
        if (g > r && g > b) return 1;
        if (b > r && b > g) return 2;
        return 3; // grey / tie: belongs to no site colour
    }

    private static void drag(SpringSaladViewerCanvas c, int x1, int y1, int x2, int y2) {
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, x1, y1, 1, false));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, 0, 0, x2, y2, 0, false));
    }

    private static void assertPicksMatchRendering(SpringSaladViewerCanvas c, String orientation) {
        Map<Integer, int[]> centroids = renderedCentroids(c);
        assertTrue(centroids.size() >= 2, orientation + ": expected at least two sites visible");
        centroids.forEach((id, xy) -> {
            SpringSaladTrajectory.Site picked = c.pickSite(xy[0], xy[1], W, H);
            assertNotNull(picked, orientation + ": nothing picked where site " + id + " is drawn");
            assertEquals(id, picked.getId(),
                    orientation + ": site " + id + " is drawn at (" + xy[0] + "," + xy[1]
                            + ") but picking there returns site " + picked.getId());
        });
    }

    @Test
    @DisplayName("in the default view, clicking a site picks the site that is drawn there")
    void pickMatchesRenderingAtRest() {
        assertPicksMatchRendering(canvas(), "default view");
    }

    @Test
    @DisplayName("still consistent after a horizontal mouse drag")
    void pickMatchesRenderingAfterHorizontalDrag() {
        SpringSaladViewerCanvas c = canvas();
        drag(c, W / 2, H / 2, W / 2 + 70, H / 2);
        assertPicksMatchRendering(c, "after dragging right");
    }

    @Test
    @DisplayName("still consistent after a vertical mouse drag")
    void pickMatchesRenderingAfterVerticalDrag() {
        // The axis where a sign error hides: horizontal drags across the midline are identical
        // under either y convention, so only this one exercises the canvas's y-flip.
        SpringSaladViewerCanvas c = canvas();
        drag(c, W / 2, H / 2, W / 2, H / 2 + 70);
        assertPicksMatchRendering(c, "after dragging down");
    }

    @Test
    @DisplayName("still consistent after a diagonal drag, zoom and pan")
    void pickMatchesRenderingAfterCompoundView() {
        SpringSaladViewerCanvas c = canvas();
        drag(c, W / 2, H / 2, W / 2 + 55, H / 2 - 40);
        c.dispatchEvent(new java.awt.event.MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, 0, 0,
                W / 2, H / 2, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -2));
        // shift-drag pans
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.SHIFT_DOWN_MASK,
                W / 2, H / 2, 1, false));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.SHIFT_DOWN_MASK,
                W / 2 + 25, H / 2 + 15, 0, false));
        assertPicksMatchRendering(c, "after rotate + zoom + pan");
    }

    @Test
    @DisplayName("empty space picks nothing, and hidden site types are not pickable")
    void emptySpaceAndHiddenTypes() {
        SpringSaladViewerCanvas c = canvas();
        assertNull(c.pickSite(2, 2, W, H), "a corner of empty space should pick nothing");

        Map<Integer, int[]> centroids = renderedCentroids(c);
        int[] redAt = centroids.get(10);
        assertNotNull(redAt);
        SpringSaladTrajectory t = c.getTrajectory();
        String key = t.siteTypeKey(t.getFrames().get(0).getSites().get(0));
        c.setSiteTypeVisible(key, false);
        SpringSaladTrajectory.Site picked = c.pickSite(redAt[0], redAt[1], W, H);
        assertTrue(picked == null || picked.getId() != 10,
                "a hidden site type must not be pickable");
    }

    // ---- hidden surface removal: paint and pick must agree on which ball is in front ----

    /**
     * The world direction pointing at the camera in the canvas's default view, so two sites placed
     * at +/- this land on the same screen pixel with one squarely behind the other.
     * Mirrors resetView(): DEFAULT_ELEVATION_DEG 35, DEFAULT_AZIMUTH_DEG 30.
     */
    private static org.springsalad.render.Vect3d towardCamera(double distance) {
        org.springsalad.render.Trackball tb = new org.springsalad.render.Trackball(
                new org.springsalad.render.Camera(),
                org.springsalad.render.Trackball.Handedness.RIGHT_HANDED);
        tb.getCamera().resetView();
        tb.setRotation(Math.toRadians(35 - 90), Math.toRadians(30), 0);
        org.springsalad.render.Affine inv = new org.springsalad.render.Affine();
        tb.getInvMatrixGL(inv);
        return inv.mult(new org.springsalad.render.Vect3d(0, 0, distance));
    }

    /** One ball directly behind the other: {@code frontColor} nearer the camera. */
    private static SpringSaladViewerCanvas occludingPair(String frontColor, String backColor) {
        org.springsalad.render.Vect3d f = towardCamera(15);
        List<SpringSaladTrajectory.Site> sites = new ArrayList<>(List.of(
                new SpringSaladTrajectory.Site(1, 4.0, frontColor, f.getX(), f.getY(), f.getZ()),
                new SpringSaladTrajectory.Site(2, 4.0, backColor, -f.getX(), -f.getY(), -f.getZ())));
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        c.setTrajectory(new SpringSaladTrajectory(1e-3, 1e-4, 50, 50, 10, 90,
                List.of(new SpringSaladTrajectory.Frame(0, 0, sites, new ArrayList<>()))));
        c.setSize(W, H);
        c.setShowBox(false);
        c.setShowMembrane(false);
        c.setShowLinks(false);
        return c;
    }

    /** Centroid of everything drawn, and which colour channel dominates there. */
    private static int[] centroidOfDrawn(BufferedImage img) {
        long sx = 0, sy = 0, n = 0;
        int bg = img.getRGB(0, 0);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getRGB(x, y) != bg) { sx += x; sy += y; n++; }
            }
        }
        return n == 0 ? null : new int[]{(int) (sx / n), (int) (sy / n)};
    }

    private static void assertFrontWinsBothWays(String frontColor, String backColor) {
        SpringSaladViewerCanvas c = occludingPair(frontColor, backColor);
        BufferedImage img = c.renderToImage(W, H);
        int[] at = centroidOfDrawn(img);
        assertNotNull(at, frontColor + "/" + backColor + ": nothing was drawn");

        // 1. the nearer ball is the one you see
        assertTrue(isShadeOf(img.getRGB(at[0], at[1]),
                        SpringSaladViewerCanvas.colorForName(frontColor)),
                "expected " + frontColor + " (front) to occlude " + backColor + " (behind), but the"
                        + " pixel at the overlap is not a shade of " + frontColor);

        // 2. the back ball is not visible anywhere -- it is squarely behind, not merely overdrawn
        int backPixels = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (isShadeOf(img.getRGB(x, y), SpringSaladViewerCanvas.colorForName(backColor))) {
                    backPixels++;
                }
            }
        }
        assertEquals(0, backPixels,
                backColor + " (behind) is visible through " + frontColor + ": depth sorting is wrong");

        // 3. and clicking there picks the ball you can actually see
        SpringSaladTrajectory.Site picked = c.pickSite(at[0], at[1], W, H);
        assertNotNull(picked, "nothing picked where the balls are drawn");
        assertEquals(1, picked.getId(),
                "the visible ball is " + frontColor + " (id 1) but picking returns id "
                        + picked.getId() + ": paint and pick disagree about which is nearer");
        assertEquals(frontColor, picked.getColor());
    }

    @Test
    @DisplayName("red in front of blue: red is drawn and red is picked")
    void redInFrontOccludesAndPicksRed() {
        assertFrontWinsBothWays("RED", "BLUE");
    }

    @Test
    @DisplayName("blue in front of red: blue is drawn and blue is picked")
    void blueInFrontOccludesAndPicksBlue() {
        // The same scene with the pair swapped. Passing both ways is the point: it shows the
        // outcome follows depth, not the order the sites happen to sit in the frame's list.
        assertFrontWinsBothWays("BLUE", "RED");
    }
}
