package org.springsalad.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the ported canvas offscreen. This runs headless, so it stands in for the handoff's
 * suggested throwaway {@code main()} and keeps working in CI.
 *
 * <p>These assert that the renderer <em>draws</em> and that its toggles change what is drawn --
 * not that it looks right. Visual parity against the Java3D viewer is checked by hand.
 */
class SpringSaladViewerCanvasTest {

    private static SpringSaladTrajectory trajectory(boolean withIdentities) throws IOException {
        SpringSaladTrajectory t;
        try (Reader r = open("/viewer/example_VIEW_Run0.txt")) {
            t = SpringSaladTrajectory.parse(r);
        }
        if (!withIdentities) {
            return t;
        }
        Map<Integer, SpringSaladTrajectory.SiteIdentity> ids;
        try (Reader r = open("/viewer/example_SiteIDs.csv")) {
            ids = SpringSaladTrajectory.parseSiteIdentities(r);
        }
        return t.withSiteIdentities(ids);
    }

    private static Reader open(String resource) {
        InputStream in = SpringSaladViewerCanvasTest.class.getResourceAsStream(resource);
        assertNotNull(in, "missing test resource " + resource);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    /**
     * A trajectory built for rendering, not parsing: two site types at distinct positions and
     * colours, and links long enough to be drawn.
     *
     * <p>The real fixture is a poor renderer subject. In that model each molecule's two sites sit
     * on top of each other (~0.2 apart at radius 1.0), so the canvas correctly suppresses their
     * bonds as overlapping, and hiding one site type leaves an identical-looking site drawn on the
     * same pixels. Both make toggle assertions vacuous.
     */
    private static SpringSaladTrajectory synthetic() {
        List<SpringSaladTrajectory.Frame> frames = new ArrayList<>();
        for (int f = 0; f < 3; f++) {
            List<SpringSaladTrajectory.Site> sites = new ArrayList<>();
            List<int[]> links = new ArrayList<>();
            for (int m = 0; m < 4; m++) {
                int base = 100 + m * 10;
                double x = -20 + m * 12 + f * 2;
                sites.add(new SpringSaladTrajectory.Site(base, 1.0, "RED", x, -8, 0));
                sites.add(new SpringSaladTrajectory.Site(base + 1, 1.5, "BLUE", x, 8, 0));
                links.add(new int[]{base, base + 1});
            }
            frames.add(new SpringSaladTrajectory.Frame(f, f * 1.0e-4, sites, links));
        }
        Map<Integer, SpringSaladTrajectory.SiteIdentity> ids = new LinkedHashMap<>();
        for (int m = 0; m < 4; m++) {
            int base = 100 + m * 10;
            ids.put(base, new SpringSaladTrajectory.SiteIdentity("MT0", 0, "Site0"));
            ids.put(base + 1, new SpringSaladTrajectory.SiteIdentity("MT0", 1, "Site1"));
        }
        return new SpringSaladTrajectory(1.0e-3, 1.0e-4, 50, 50, 10, 90, frames, ids);
    }

    private static SpringSaladViewerCanvas syntheticCanvas() {
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        c.setTrajectory(synthetic());
        return c;
    }

    private static SpringSaladViewerCanvas canvas(boolean withIdentities) throws IOException {
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        c.setTrajectory(trajectory(withIdentities));
        return c;
    }

    /** Distinct RGB values in the image; a proxy for "something was actually drawn". */
    private static Set<Integer> colors(BufferedImage img) {
        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                seen.add(img.getRGB(x, y));
            }
        }
        return seen;
    }

    /** Digest of every pixel. Short enough to read in a failure message, exact enough to compare. */
    private static String signature(BufferedImage img) {
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                md.update((byte) (rgb >> 16));
                md.update((byte) (rgb >> 8));
                md.update((byte) rgb);
            }
        }
        StringBuilder sb = new StringBuilder();
        byte[] d = md.digest();
        for (int i = 0; i < 6; i++) {
            sb.append(String.format("%02x", d[i]));
        }
        return sb.toString();
    }

    /** Pixels that are not the background colour -- i.e. actual scene content. */
    private static int drawnPixels(BufferedImage img) {
        int background = img.getRGB(0, 0);
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getRGB(x, y) != background) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    @DisplayName("renders the trajectory offscreen without a display")
    void rendersHeadless() throws IOException {
        assertTrue(java.awt.GraphicsEnvironment.isHeadless(), "test must prove headless rendering");
        BufferedImage img = canvas(true).renderToImage(400, 300);
        assertEquals(400, img.getWidth());
        assertEquals(300, img.getHeight());
        assertTrue(colors(img).size() > 1, "image is blank -- nothing was drawn");
    }

    @Test
    @DisplayName("frame count comes from the trajectory and frames render differently")
    void framesDiffer() throws IOException {
        SpringSaladViewerCanvas c = canvas(true);
        assertEquals(2, c.getFrameCount());
        assertNotEquals(signature(c.renderFrameToImage(0, 200, 160)),
                signature(c.renderFrameToImage(1, 200, 160)),
                "both frames rendered identically; the frame index is not being applied");
    }

    @Test
    @DisplayName("renderFrameToImage does not disturb the displayed frame")
    void offscreenRenderIsSideEffectFree() throws IOException {
        SpringSaladViewerCanvas c = canvas(true);
        c.setFrameIndex(0);
        c.renderFrameToImage(1, 100, 100);
        assertEquals(0, c.getFrameIndex());
    }

    @Test
    @DisplayName("hiding a site type changes the rendering; showAll restores it")
    void siteTypeTogglesAffectRendering() {
        SpringSaladViewerCanvas c = syntheticCanvas();
        String before = signature(c.renderToImage(240, 200));

        c.setSiteTypeVisible("site:MT0 Site0", false);
        assertFalse(c.isSiteTypeVisible("site:MT0 Site0"));
        assertNotEquals(before, signature(c.renderToImage(240, 200)),
                "hiding a site type did not change the image");

        c.showAllSiteTypes();
        assertTrue(c.isSiteTypeVisible("site:MT0 Site0"));
        assertEquals(before, signature(c.renderToImage(240, 200)), "showAll did not restore the view");
    }

    @Test
    @DisplayName("the site type keys the canvas hides by are the ones SiteIDs.csv produces")
    void toggleKeysMatchTrajectoryKeys() {
        // Guards the wiring between the legend and the canvas: both must agree on the key.
        SpringSaladTrajectory t = synthetic();
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        c.setTrajectory(t);
        for (SpringSaladTrajectory.Site s : t.getFrames().get(0).getSites()) {
            String key = t.siteTypeKey(s);
            c.setSiteTypeVisible(key, false);
            assertFalse(c.isSiteTypeVisible(key));
        }
        String allHidden = signature(c.renderToImage(240, 200));
        c.showAllSiteTypes();
        assertNotEquals(allHidden, signature(c.renderToImage(240, 200)));
    }

    @Test
    @DisplayName("box and link toggles change the rendering")
    void boxAndLinkTogglesAffectRendering() {
        SpringSaladViewerCanvas c = syntheticCanvas();
        String before = signature(c.renderToImage(240, 200));
        c.setShowBox(false);
        assertNotEquals(before, signature(c.renderToImage(240, 200)), "box toggle had no effect");
        c.setShowBox(true);
        c.setShowLinks(false);
        assertNotEquals(before, signature(c.renderToImage(240, 200)), "link toggle had no effect");
    }

    @Test
    @DisplayName("rotating changes the view; resetView returns to the original")
    void rotateAndReset() {
        SpringSaladViewerCanvas c = syntheticCanvas();
        String before = signature(c.renderToImage(240, 200));
        c.rotate(0.0, 0.0, 0.4, 0.3);
        assertNotEquals(before, signature(c.renderToImage(240, 200)), "rotate had no effect");
        c.resetView();
        assertEquals(before, signature(c.renderToImage(240, 200)), "resetView did not restore");
    }

    @Test
    @DisplayName("renders a trajectory with no site identities (older runs)")
    void rendersWithoutSiteIdentities() throws IOException {
        BufferedImage img = canvas(false).renderToImage(200, 160);
        assertTrue(colors(img).size() > 1, "blank image for a trajectory without SiteIDs.csv");
    }

    @Test
    @DisplayName("an empty canvas draws a message rather than throwing or rendering blank")
    void emptyCanvasIsSafe() {
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        assertEquals(0, c.getFrameCount());
        BufferedImage img = c.renderToImage(200, 90);
        int drawn = drawnPixels(img);
        assertTrue(drawn > 0 && drawn < 2000,
                "expected the 'No trajectory data.' message, but " + drawn + " pixels were drawn");
    }

    @Test
    @DisplayName("bonds between overlapping spheres are suppressed, not drawn through them")
    void overlappingSitesDrawNoBond() {
        // Why the real fixture cannot test the link toggle: its paired sites are ~0.2 apart at
        // radius 1.0, so every bond is correctly suppressed.
        List<SpringSaladTrajectory.Site> sites = List.of(
                new SpringSaladTrajectory.Site(1, 1.0, "RED", 0, 0, 0),
                new SpringSaladTrajectory.Site(2, 1.0, "RED", 0.2, 0, 0));
        SpringSaladTrajectory t = new SpringSaladTrajectory(1e-3, 1e-4, 50, 50, 10, 90,
                List.of(new SpringSaladTrajectory.Frame(0, 0, new ArrayList<>(sites),
                        new ArrayList<>(List.of(new int[]{1, 2})))));
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        c.setTrajectory(t);
        String withLinks = signature(c.renderToImage(200, 160));
        c.setShowLinks(false);
        assertEquals(withLinks, signature(c.renderToImage(200, 160)),
                "a bond was drawn between spheres that overlap");
    }
}
