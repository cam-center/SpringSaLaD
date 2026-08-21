package org.springsalad.langevinsetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The molecule editor's structure view, which used to be a Java3D {@code Canvas3D} over a
 * {@code SimpleUniverse} and is now Java2D.
 *
 * <p>The behaviour worth pinning is the same as for the trajectory viewer: that it draws, that
 * clicking selects what is actually drawn at that point, and that selection round-trips with the
 * rest of the editor. Rendering correctness beyond "something got drawn" is a matter for the eye.
 */
class DrawPanel3DTest {

    private static final int W = 320, H = 320;

    private Molecule molecule;
    private Site a, b, c;

    @BeforeEach
    void buildMolecule() {
        molecule = new Molecule("MT0");
        molecule.setLocation(SystemGeometry.INSIDE);
        a = site("Site0", "RED", -12, 0, 0, 3.0);
        b = site("Site1", "BLUE", 12, 0, 0, 3.0);
        c = site("Site2", "LIME", 0, 14, 0, 3.0);
        molecule.addSite(a);
        molecule.addSite(b);
        molecule.addSite(c);
        molecule.addLink(new Link(a, b));
    }

    private Site site(String typeName, String color, double x, double y, double z, double radius) {
        SiteType type = new SiteType(molecule, typeName);
        type.setColor(color);
        type.setRadius(radius);
        Site s = new Site(molecule, type);
        s.setLocation(SystemGeometry.INSIDE);
        s.setX(x);
        s.setY(y);
        s.setZ(z);
        return s;
    }

    private DrawPanel3D panel() {
        DrawPanel3D p = new DrawPanel3D(molecule);
        p.setSize(W, H);
        return p;
    }

    private static int drawnPixels(BufferedImage img) {
        int bg = img.getRGB(0, 0), n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getRGB(x, y) != bg) n++;
            }
        }
        return n;
    }

    /** Screen position where a site is actually drawn, found by rendering. */
    private static int[] where(DrawPanel3D p, Site site) {
        for (int y = 0; y < H; y += 1) {
            for (int x = 0; x < W; x += 1) {
                if (p.siteAt(x, y) == site) {   // identity: Site.equals(null) throws
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static void click(DrawPanel3D p, int x, int y) {
        clickWith(p, x, y, 0);
    }

    private static void ctrlClick(DrawPanel3D p, int x, int y) {
        clickWith(p, x, y, MouseEvent.CTRL_DOWN_MASK);
    }

    private static void clickWith(DrawPanel3D p, int x, int y, int mods) {
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, 0, mods, x, y, 1, false));
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, 0, mods, x, y, 1, false));
    }

    @Test
    @DisplayName("renders the molecule headless, with no Java3D")
    void rendersHeadless() {
        assertTrue(java.awt.GraphicsEnvironment.isHeadless());
        BufferedImage img = panel().renderToImage(W, H);
        assertTrue(drawnPixels(img) > 100, "nothing was drawn");
    }

    @Test
    @DisplayName("each site's colour reaches the screen")
    void sitesKeepTheirColours() {
        BufferedImage img = panel().renderToImage(W, H);
        Set<Integer> hues = new HashSet<>();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = img.getRGB(x, y), r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
                if (r + g + bl < 40) continue;
                if (r > g && r > bl) hues.add(0);
                else if (g > r && g > bl) hues.add(1);
                else if (bl > r && bl > g) hues.add(2);
            }
        }
        assertEquals(Set.of(0, 1, 2), hues,
                "expected a red, a blue and a green site; the site type colours are not being used");
    }

    @Test
    @DisplayName("clicking a site picks the site drawn at that point")
    void clickPicksWhatIsDrawn() {
        DrawPanel3D p = panel();
        for (Site s : List.of(a, b, c)) {
            int[] at = where(p, s);
            assertNotNull(at, "no pixel picks site " + s.getTypeName());
            assertSame(s, p.siteAt(at[0], at[1]));
        }
    }

    @Test
    @DisplayName("a click selects, and notifies the rest of the editor")
    void clickSelectsAndNotifies() {
        DrawPanel3D p = panel();
        List<MoleculeSelectionEvent> events = new ArrayList<>();
        p.addMoleculeSelectionListener(events::add);

        int[] at = where(p, a);
        assertNotNull(at);
        click(p, at[0], at[1]);

        assertEquals(List.of(a), p.getSelectedSites());
        assertTrue(events.size() >= 1, "no selection event was fired");
        assertEquals(List.of(a), events.get(events.size() - 1).getSelectedSites());
    }

    @Test
    @DisplayName("clicking the selected site again clears it; empty space clears too")
    void clickingTogglesAndEmptySpaceClears() {
        DrawPanel3D p = panel();
        int[] at = where(p, a);
        click(p, at[0], at[1]);
        assertEquals(List.of(a), p.getSelectedSites());
        click(p, at[0], at[1]);
        assertEquals(List.of(), p.getSelectedSites(), "clicking a selected site should clear it");

        click(p, at[0], at[1]);
        click(p, 1, 1);
        assertEquals(List.of(), p.getSelectedSites(), "clicking empty space should clear the selection");
    }

    @Test
    @DisplayName("selection made elsewhere in the editor is mirrored here")
    void incomingSelectionIsMirrored() {
        DrawPanel3D p = panel();
        p.selectionOccurred(new MoleculeSelectionEvent(
                new ArrayList<>(List.of(b)), new ArrayList<>()));
        assertEquals(List.of(b), p.getSelectedSites());
    }

    @Test
    @DisplayName("a selected site is drawn differently from an unselected one")
    void selectionIsVisible() {
        DrawPanel3D p = panel();
        int before = drawnPixels(p.renderToImage(W, H));
        p.selectionOccurred(new MoleculeSelectionEvent(new ArrayList<>(List.of(a)), new ArrayList<>()));
        assertNotEquals(before, drawnPixels(p.renderToImage(W, H)),
                "selecting a site changed nothing on screen");
    }

    @Test
    @DisplayName("dragging rotates instead of selecting")
    void dragRotatesAndDoesNotSelect() {
        DrawPanel3D p = panel();
        String before = signature(p.renderToImage(W, H));
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, 0, 0, W / 2, H / 2, 1, false));
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_DRAGGED, 0, 0, W / 2 + 60, H / 2, 0, false));
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, 0, 0, W / 2 + 60, H / 2, 1, false));
        assertNotEquals(before, signature(p.renderToImage(W, H)), "the drag did not rotate the view");
        assertEquals(List.of(), p.getSelectedSites(), "a drag must not select");
    }

    private static String signature(BufferedImage img) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < img.getHeight(); y += 8) {
            for (int x = 0; x < img.getWidth(); x += 8) {
                sb.append(img.getRGB(x, y) == img.getRGB(0, 0) ? '.' : '#');
            }
        }
        return sb.toString();
    }

    @Test
    @DisplayName("flip mirrors non-anchor sites through the chosen plane")
    void flipMirrorsCoordinates() {
        DrawPanel3D p = panel();
        double x = a.getX(), y = c.getY();
        p.flip(0);
        assertEquals(-x, a.getX(), 1e-12);
        p.flip(1);
        assertEquals(-y, c.getY(), 1e-12);
    }

    @Test
    @DisplayName("overlapping sites are reported, non-overlapping ones are not")
    void overlapsAreDetected() {
        assertEquals(Set.of(), panel().getOverlaps().keySet(), "these sites do not overlap");
        b.setX(a.getX() + 1);   // radii are 3.0 each, so now they intersect
        assertEquals(Set.of(a, b), panel().getOverlaps().keySet());
    }

    @Test
    @DisplayName("a molecule with no sites renders a message instead of throwing")
    void emptyMoleculeIsSafe() {
        DrawPanel3D p = new DrawPanel3D(new Molecule("empty"));
        p.setSize(W, H);
        assertTrue(drawnPixels(p.renderToImage(W, H)) > 0);
        assertNull(p.siteAt(W / 2, H / 2));
    }

    @Test
    @DisplayName("ctrl-click adds to the selection instead of replacing it")
    void ctrlClickAccumulates() {
        // The Java3D version tracked ctrl with a KeyListener, which only sees the key while this
        // component has focus -- and leaves it stuck down if you release it elsewhere. The
        // modifier is read from the mouse event instead, so this works regardless of focus.
        DrawPanel3D p = panel();
        click(p, where(p, a)[0], where(p, a)[1]);
        assertEquals(List.of(a), p.getSelectedSites());

        ctrlClick(p, where(p, b)[0], where(p, b)[1]);
        assertEquals(List.of(a, b), p.getSelectedSites(), "ctrl-click replaced instead of adding");

        ctrlClick(p, where(p, b)[0], where(p, b)[1]);
        assertEquals(List.of(a), p.getSelectedSites(), "ctrl-click did not toggle the site back off");
    }

    @Test
    @DisplayName("ctrl-click on empty space keeps the selection")
    void ctrlClickOnEmptySpaceKeepsSelection() {
        DrawPanel3D p = panel();
        click(p, where(p, a)[0], where(p, a)[1]);
        ctrlClick(p, 1, 1);
        assertEquals(List.of(a), p.getSelectedSites(),
                "ctrl-click on empty space should not clear an in-progress selection");
    }

    @Test
    @DisplayName("clicking a link selects the link and nothing else")
    void clickSelectsLink() {
        DrawPanel3D p = panel();
        Link link = molecule.getLinkArray().get(0);
        int[] on = null;
        for (int y = 0; y < H && on == null; y++) {
            for (int x = 0; x < W; x++) {
                if (p.linkAt(x, y) == link && p.siteAt(x, y) == null) { on = new int[]{x, y}; break; }
            }
        }
        assertNotNull(on, "the link is not pickable anywhere clear of its end sites");
        click(p, on[0], on[1]);
        assertEquals(List.of(link), p.getSelectedLinks());
        assertEquals(List.of(), p.getSelectedSites(), "a link click should not also select a site");
    }

    // ---- links stop at the sphere surface, not at the centre ----

    /** Screen position of a site's centre: the pixel where it picks that is furthest inside it. */
    private static int[] centreOf(DrawPanel3D p, Site site) {
        long sx = 0, sy = 0, n = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (p.siteAt(x, y) == site) { sx += x; sy += y; n++; }
            }
        }
        return n == 0 ? null : new int[]{(int) (sx / n), (int) (sy / n)};
    }

    @Test
    @DisplayName("a link is cropped to the endpoint radii, so it never reaches a site's centre")
    void linkIsTruncatedAtTheSphereSurface() {
        // Cropping happens in WORLD space before projection. Drawn centre-to-centre instead, the
        // segment runs through both spheres and the centre of each is right on top of it.
        DrawPanel3D p = panel();
        Link link = molecule.getLinkArray().get(0);

        for (Site end : List.of(a, b)) {
            int[] centre = centreOf(p, end);
            assertNotNull(centre);
            assertNull(p.linkAt(centre[0], centre[1]),
                    "the link is still drawn through the centre of " + end.getTypeName()
                            + "; it is not being cropped to the sphere radius");
        }

        boolean pickableSomewhere = false;
        for (int y = 0; y < H && !pickableSomewhere; y++) {
            for (int x = 0; x < W; x++) {
                if (p.linkAt(x, y) == link) { pickableSomewhere = true; break; }
            }
        }
        assertTrue(pickableSomewhere, "cropping removed the whole link");
    }

    @Test
    @DisplayName("no link is drawn between spheres that touch or overlap")
    void overlappingSpheresDrawNoLink() {
        // Radii are 3.0 each, so 1.0 apart leaves no visible length of link between them.
        b.setX(a.getX() + 1);
        b.setY(a.getY());
        b.setZ(a.getZ());
        DrawPanel3D p = panel();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertNull(p.linkAt(x, y),
                        "a link was drawn between overlapping spheres at (" + x + "," + y + ")");
            }
        }
    }

    // ---- depth shading ----

    /** Brightest pixel of a given hue: 0 red, 1 green, 2 blue. */
    private static int peak(BufferedImage img, int hue) {
        int best = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y), r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                if (r + g + b < 40) continue;
                int dom = (r > g && r > b) ? 0 : (g > r && g > b) ? 1 : (b > r && b > g) ? 2 : -1;
                if (dom == hue) best = Math.max(best, Math.max(r, Math.max(g, b)));
            }
        }
        return best;
    }

    private static void dragRight(DrawPanel3D p, int by) {
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, 0, 0, W / 2, H / 2, 1, false));
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_DRAGGED, 0, 0, W / 2 + by, H / 2, 0, false));
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, 0, 0, W / 2 + by, H / 2, 1, false));
    }

    @Test
    @DisplayName("the depth ramp is gradual and scaled to the scene, not to the sites on screen")
    void shadingRampIsGradual() {
        // Shading used to be normalised against the nearest and furthest site on screen, so with
        // two sites the nearer was always fully lit and the further always at the floor, and the
        // two swapped abruptly as the view turned. Against the scene's own size, a small change in
        // depth is a small change in shade -- and crucially the ramp does not depend on how many
        // sites there are or how they happen to be spread.
        double radius = 10;
        double previous = DrawPanel3D.brightness(-radius, radius);
        double biggestStep = 0;
        for (double depth = -radius; depth <= radius; depth += radius / 50) {
            double now = DrawPanel3D.brightness(depth, radius);
            assertTrue(now >= previous - 1e-9, "the ramp must not go backwards as depth increases");
            biggestStep = Math.max(biggestStep, Math.abs(now - previous));
            previous = now;
        }
        assertTrue(biggestStep < 0.05,
                "a 1/50th-of-the-scene step in depth changed brightness by " + biggestStep);

        // Two sites a hair apart must not come out at opposite ends of the ramp.
        double near = DrawPanel3D.brightness(0.05, radius), far = DrawPanel3D.brightness(-0.05, radius);
        assertTrue(Math.abs(near - far) < 0.05,
                "sites 0.1 units apart in a 10-unit scene differ in brightness by "
                        + Math.abs(near - far) + "; the ramp is being stretched to fit them");
    }

    @Test
    @DisplayName("the ramp is clamped, so nothing goes black or blows out")
    void shadingRampIsClamped() {
        assertEquals(DrawPanel3D.brightness(-1000, 10), DrawPanel3D.brightness(-20, 10), 1e-9);
        assertEquals(DrawPanel3D.brightness(1000, 10), DrawPanel3D.brightness(20, 10), 1e-9);
        assertTrue(DrawPanel3D.brightness(-1000, 10) > 0.1, "the far end must stay visible");
        assertTrue(DrawPanel3D.brightness(1000, 10) <= 1.0);
    }

    @Test
    @DisplayName("a distant site is darkened, not made translucent")
    void depthShadingDoesNotUseAlpha() {
        // Applying the depth ramp as alpha would let whatever is behind a site show through it.
        // Put one site squarely behind another and check the front one is drawn solid: every pixel
        // at its centre belongs to its own hue, with nothing of the far site blended in.
        b.setX(a.getX());
        b.setY(a.getY());
        b.setZ(a.getZ() - 12);           // directly behind, well separated
        DrawPanel3D p = panel();
        BufferedImage img = p.renderToImage(W, H);

        int[] centre = centreOf(p, p.siteAt(W / 2, H / 2) == null ? a : p.siteAt(W / 2, H / 2));
        assertNotNull(centre, "neither site was drawn");
        int rgb = img.getRGB(centre[0], centre[1]);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        int dominant = Math.max(r, Math.max(g, bl));
        int second = r + g + bl - dominant - Math.min(r, Math.min(g, bl));
        assertTrue(dominant > 3 * Math.max(1, second),
                "the front site's centre is a blend (" + r + "," + g + "," + bl
                        + "); the one behind is showing through, so shading is being applied as alpha");
    }
}
