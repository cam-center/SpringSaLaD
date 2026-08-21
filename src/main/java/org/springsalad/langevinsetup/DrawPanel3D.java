package org.springsalad.langevinsetup;

import org.jogamp.vecmath.Matrix3f;
import org.jogamp.vecmath.Vector3d;
import org.springsalad.helpersetup.Colors;
import org.springsalad.render.Affine;
import org.springsalad.render.Camera;
import org.springsalad.render.Trackball;
import org.springsalad.render.Vect3d;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The molecule editor's 3D structure view: sites as shaded spheres, links between them, and the
 * membrane plane for anchored molecules. Rotate by dragging, zoom with the wheel, click to select
 * a site or link, ctrl-click to add to the selection.
 *
 * <p>Java2D only. This used to be a Java3D {@code Canvas3D} driving a {@code SimpleUniverse} with
 * {@code Sphere}/{@code Cylinder} primitives, an {@code OrbitBehavior} and a {@code PickCanvas};
 * that was the last thing in the project pulling the native jogamp libraries in. The public API is
 * unchanged, so {@link MainGUI} and {@link DrawPanel3DPanel} are untouched.
 *
 * <p>There is no scene graph any more: every paint renders straight from the molecule, so sites and
 * links that come and go need no bookkeeping. {@link #addSite}, {@link #addLink} and
 * {@link #removeLink} are kept for their callers and simply repaint.
 */
public class DrawPanel3D extends JPanel implements KeyListener, MoleculeSelectionListener,
        RotateUpdateListener {

    // Kept: LinkCylinder used these, and callers may reference them.
    public final static Vector3d x_axis = new Vector3d(1.0, 0, 0);
    public final static Vector3d y_axis = new Vector3d(0, 1.0, 0);
    public final static Vector3d z_axis = new Vector3d(0, 0, 1.0);

    private static final int SPRITE_SIZE = 96;
    private static final double MIN_BRIGHT = 0.45;
    private static final double SCREEN_FILL = 0.42;
    private static final Color LINK_COLOR = new Color(150, 150, 150);
    private static final Color HIGHLIGHT = Color.WHITE;
    private static final Color MEMBRANE_FILL = new Color(0, 128, 0, 140);
    /**
     * Default view: nearly side-on, swung slightly round. SpringSaLaD molecules are built as
     * chains running away from the membrane along z, so looking down z -- which is where an
     * un-rotated trackball starts -- stacks every site on top of the one below it.
     */
    private static final double DEFAULT_ELEVATION_DEG = 12;
    private static final double DEFAULT_AZIMUTH_DEG = 25;

    private final Molecule molecule;
    private final List<Site> selectedSites = new ArrayList<>();
    private final List<Link> selectedLinks = new ArrayList<>();
    private final List<MoleculeSelectionListener> listeners = new ArrayList<>();

    private final Trackball trackball = new Trackball(new Camera(), Trackball.Handedness.RIGHT_HANDED);
    private double zoom = 1.0, panX = 0, panY = 0;
    private int lastX, lastY;
    private boolean panning, dragged;
    private boolean ctrlPressed;

    private boolean showMembrane;
    private float xsize = 25, ysize = 25;   // half the membrane extent

    private ActionListener intListener;
    private Matrix3f m3 = new Matrix3f();

    /** Cheap sprite cache: one shaded ball per colour, tinted per depth at draw time. */
    private final Map<Color, BufferedImage> sprites = new HashMap<>();

    public DrawPanel3D(Molecule molecule) {
        this.molecule = molecule;
        setPreferredSize(new Dimension(1000, 500));
        setBackground(Color.BLACK);
        setFocusable(true);

        if (molecule.hasAnchorSites()) {
            molecule.translate(0, 0, -molecule.membranePosition());
        }
        for (Site site : molecule.getAnchorSites()) {
            xsize = (float) Math.max(xsize, Math.abs(site.getX() + site.getRadius()));
            ysize = (float) Math.max(ysize, Math.abs(site.getY() + site.getRadius()));
        }
        xsize *= 1.1f;
        ysize *= 1.1f;

        resetView();

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                lastX = e.getX(); lastY = e.getY();
                panning = e.isShiftDown() || javax.swing.SwingUtilities.isRightMouseButton(e);
                dragged = false;
            }
            @Override public void mouseDragged(MouseEvent e) {
                int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
                if (panning) {
                    panX += e.getX() - lastX;
                    panY += e.getY() - lastY;
                } else {
                    trackball.rotate_xy(2.0 * lastX / w - 1.0, 1.0 - 2.0 * lastY / h,
                            2.0 * e.getX() / w - 1.0, 1.0 - 2.0 * e.getY() / h);
                }
                lastX = e.getX(); lastY = e.getY();
                dragged = true;
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (dragged) {
                    publishRotation();      // keep Jmol in step with the view
                } else {
                    pickAt(e.getX(), e.getY());
                }
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                zoom = Math.max(0.1, Math.min(50.0, zoom * Math.pow(1.1, -e.getPreciseWheelRotation())));
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        addKeyListener(this);
    }

    /* ******************** view setup ******************** */

    /** Frame the molecule side-on again, undoing any rotation, zoom and pan. */
    public void resetView() {
        trackball.getCamera().resetView();
        trackball.setRotation(Math.toRadians(DEFAULT_ELEVATION_DEG - 90),
                Math.toRadians(DEFAULT_AZIMUTH_DEG), 0);
        zoom = 1.0;
        panX = 0;
        panY = 0;
        repaint();
    }

    public void addMembrane(boolean bool) {
        showMembrane = bool;
        repaint();
    }

    public void systemSetup() {
        addMembrane(molecule.getLocation().equals(SystemGeometry.MEMBRANE));
    }

    /* ******************** rendering ******************** */

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintScene(g2, getWidth(), getHeight());
    }

    /** Render offscreen at an arbitrary size; used by the tests, which run headless. */
    public BufferedImage renderToImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(getBackground());
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintScene(g, w, h);
        g.dispose();
        return img;
    }

    private void paintScene(Graphics2D g2, int w, int h) {
        List<Site> sites = molecule.getSiteArray();
        if (sites.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.drawString("No sites in this molecule.", 12, 20);
            return;
        }
        Affine rot = new Affine();
        trackball.getMatrixGL(rot);
        double radius = sceneRadius();
        double scale = SCREEN_FILL * Math.min(w, h) / radius * zoom;
        double ox = w / 2.0 + panX, oy = h / 2.0 + panY;

        Map<Site, double[]> projected = new HashMap<>();
        double minD = Double.POSITIVE_INFINITY, maxD = Double.NEGATIVE_INFINITY;
        for (Site s : sites) {
            double[] p = project(rot, s.getX(), s.getY(), s.getZ(), scale, ox, oy);
            projected.put(s, p);
            minD = Math.min(minD, p[2]);
            maxD = Math.max(maxD, p[2]);
        }
        double span = (maxD - minD) < 1e-12 ? 1 : maxD - minD;

        List<Drawable> drawables = new ArrayList<>();
        if (showMembrane) {
            addMembraneQuad(rot, scale, ox, oy, drawables);
        }
        for (Link link : molecule.getLinkArray()) {
            double[] a = projected.get(link.getSite1()), b = projected.get(link.getSite2());
            if (a == null || b == null) {
                continue;   // a site was removed but the link has not been cleaned up yet
            }
            boolean on = selectedLinks.contains(link);
            Line2D line = new Line2D.Double(a[0], a[1], b[0], b[1]);
            drawables.add(new Drawable((a[2] + b[2]) / 2, gg -> {
                gg.setColor(on ? HIGHLIGHT : LINK_COLOR);
                gg.setStroke(new BasicStroke(on ? 3f : 1.5f));
                gg.draw(line);
            }));
        }
        for (Site s : sites) {
            double[] p = projected.get(s);
            double r = Math.max(2.0, s.getRadius() * scale);
            double bright = MIN_BRIGHT + (1 - MIN_BRIGHT) * ((p[2] - minD) / span);
            BufferedImage sprite = sprite(siteColor(s));
            boolean on = selectedSites.contains(s);
            drawables.add(new Drawable(p[2], gg -> {
                java.awt.Composite old = gg.getComposite();
                gg.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, (float) bright));
                gg.drawImage(sprite, (int) Math.round(p[0] - r), (int) Math.round(p[1] - r),
                        (int) Math.round(2 * r), (int) Math.round(2 * r), null);
                gg.setComposite(old);
                if (on) {
                    gg.setColor(HIGHLIGHT);
                    gg.setStroke(new BasicStroke(2f));
                    gg.draw(new Ellipse2D.Double(p[0] - r - 2, p[1] - r - 2, 2 * r + 4, 2 * r + 4));
                }
            }));
        }
        drawables.sort(Comparator.comparingDouble(d -> d.depth));   // far first
        for (Drawable d : drawables) {
            d.paint.accept(g2);
        }
    }

    private void addMembraneQuad(Affine rot, double scale, double ox, double oy, List<Drawable> out) {
        double[][] corners = {{-xsize, -ysize}, {xsize, -ysize}, {xsize, ysize}, {-xsize, ysize}};
        Path2D.Double quad = new Path2D.Double();
        double depth = 0;
        for (int i = 0; i < corners.length; i++) {
            double[] p = project(rot, corners[i][0], corners[i][1], 0, scale, ox, oy);
            depth += p[2] / corners.length;
            if (i == 0) quad.moveTo(p[0], p[1]); else quad.lineTo(p[0], p[1]);
        }
        quad.closePath();
        out.add(new Drawable(depth, gg -> {
            gg.setColor(MEMBRANE_FILL);
            gg.fill(quad);
            gg.setColor(MEMBRANE_FILL.darker());
            gg.setStroke(new BasicStroke(1f));
            gg.draw(quad);
        }));
    }

    /**
     * Centre of what is on screen. Framing on the molecule's own bounding box, not on the origin:
     * a molecule built a few units off-origin would otherwise be drawn small and pushed to one
     * side, because the view would be sized by its distance from a point it does not occupy.
     */
    private double[] sceneCenter() {
        List<Site> sites = molecule.getSiteArray();
        if (sites.isEmpty()) {
            return new double[]{0, 0, 0, 1};   // 4th element is the radius; keep the shape
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Site s : sites) {
            minX = Math.min(minX, s.getX() - s.getRadius());
            maxX = Math.max(maxX, s.getX() + s.getRadius());
            minY = Math.min(minY, s.getY() - s.getRadius());
            maxY = Math.max(maxY, s.getY() + s.getRadius());
            minZ = Math.min(minZ, s.getZ() - s.getRadius());
            maxZ = Math.max(maxZ, s.getZ() + s.getRadius());
        }
        if (showMembrane) {   // keep the membrane quad in frame too
            minX = Math.min(minX, -xsize); maxX = Math.max(maxX, xsize);
            minY = Math.min(minY, -ysize); maxY = Math.max(maxY, ysize);
            minZ = Math.min(minZ, 0);      maxZ = Math.max(maxZ, 0);
        }
        return new double[]{(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
                Math.max(1e-9, Math.sqrt(Math.pow(maxX - minX, 2) + Math.pow(maxY - minY, 2)
                        + Math.pow(maxZ - minZ, 2)) / 2)};
    }

    private double sceneRadius() {
        return sceneCenter()[3];
    }

    private double[] project(Affine rot, double x, double y, double z, double scale, double ox, double oy) {
        double[] c = sceneCenter();
        Vect3d v = rot.mult(new Vect3d(x - c[0], y - c[1], z - c[2]));
        return new double[]{ox + v.getX() * scale, oy - v.getY() * scale, v.getZ()};
    }

    private static Color siteColor(Site site) {
        Color c = site.getType() == null ? null : site.getType().getColor();
        return c == null ? Colors.RED.getColor() : c;
    }

    /** A lit sphere impostor, cached per colour. */
    private BufferedImage sprite(Color color) {
        return sprites.computeIfAbsent(color, c -> {
            int n = SPRITE_SIZE;
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            double r = n / 2.0;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double dx = (x - r + 0.5) / r, dy = (y - r + 0.5) / r;
                    double d2 = dx * dx + dy * dy;
                    if (d2 > 1.0) {
                        continue;
                    }
                    double nz = Math.sqrt(1.0 - d2);
                    // light from the upper left, toward the viewer
                    double lambert = Math.max(0.0, -0.4 * dx - 0.5 * dy + 0.75 * nz);
                    double shade = 0.25 + 0.75 * lambert;
                    int rr = clamp(c.getRed() * shade), gg = clamp(c.getGreen() * shade),
                            bb = clamp(c.getBlue() * shade);
                    img.setRGB(x, y, (255 << 24) | (rr << 16) | (gg << 8) | bb);
                }
            }
            return img;
        });
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private record Drawable(double depth, Consumer<Graphics2D> paint) { }

    /* ******************** picking and selection ******************** */

    /**
     * Select whatever is under the cursor, honouring the same rules the Java3D version used:
     * ctrl-click toggles, a plain click on an unselected thing replaces the selection, a plain
     * click on an already-selected thing clears it, and a click on empty space clears unless ctrl
     * is held.
     */
    private void pickAt(int mx, int my) {
        Site site = siteAt(mx, my);
        if (site != null) {
            if (ctrlPressed) {
                if (!selectedSites.remove(site)) {
                    selectedSites.add(site);
                }
            } else if (selectedSites.contains(site)) {
                clearSelection();
            } else {
                clearSelection();
                selectedSites.add(site);
            }
            notifyListeners();
            repaint();
            return;
        }
        Link link = linkAt(mx, my);
        if (link != null) {
            if (ctrlPressed) {
                if (!selectedLinks.remove(link)) {
                    selectedLinks.add(link);
                }
            } else if (selectedLinks.contains(link)) {
                clearSelection();
            } else {
                clearSelection();
                selectedLinks.add(link);
            }
            notifyListeners();
            repaint();
            return;
        }
        if (!ctrlPressed) {
            clearSelection();
            notifyListeners();
            repaint();
        }
    }

    /** The frontmost site drawn at a screen point, or null. Public so the tests can pick. */
    public Site siteAt(int mx, int my) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        Affine rot = new Affine();
        trackball.getMatrixGL(rot);
        double scale = SCREEN_FILL * Math.min(w, h) / sceneRadius() * zoom;
        double ox = w / 2.0 + panX, oy = h / 2.0 + panY;
        Site best = null;
        double bestDepth = Double.NEGATIVE_INFINITY;
        for (Site s : molecule.getSiteArray()) {
            double[] p = project(rot, s.getX(), s.getY(), s.getZ(), scale, ox, oy);
            double r = Math.max(2.0, s.getRadius() * scale);
            double dx = mx - p[0], dy = my - p[1];
            if (dx * dx + dy * dy <= r * r && p[2] > bestDepth) {
                bestDepth = p[2];
                best = s;
            }
        }
        return best;
    }

    /** The link whose drawn segment passes closest to a screen point, within a few pixels. */
    public Link linkAt(int mx, int my) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        Affine rot = new Affine();
        trackball.getMatrixGL(rot);
        double scale = SCREEN_FILL * Math.min(w, h) / sceneRadius() * zoom;
        double ox = w / 2.0 + panX, oy = h / 2.0 + panY;
        Link best = null;
        double bestDist = 5.0;   // pixels
        for (Link link : molecule.getLinkArray()) {
            double[] a = project(rot, link.getX1(), link.getY1(), link.getZ1(), scale, ox, oy);
            double[] b = project(rot, link.getX2(), link.getY2(), link.getZ2(), scale, ox, oy);
            double d = Line2D.ptSegDist(a[0], a[1], b[0], b[1], mx, my);
            if (d < bestDist) {
                bestDist = d;
                best = link;
            }
        }
        return best;
    }

    private void clearSelection() {
        selectedSites.clear();
        selectedLinks.clear();
    }

    public ArrayList<Site> getSelectedSites() {
        return new ArrayList<>(selectedSites);
    }

    public ArrayList<Link> getSelectedLinks() {
        return new ArrayList<>(selectedLinks);
    }

    /* ******************** selection listeners ******************** */

    public void addMoleculeSelectionListener(MoleculeSelectionListener listener) {
        listeners.add(listener);
    }

    public void removeMoleculeSelectionListener(MoleculeSelectionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        MoleculeSelectionEvent event =
                new MoleculeSelectionEvent(new ArrayList<>(selectedSites), new ArrayList<>(selectedLinks));
        for (MoleculeSelectionListener listener : listeners) {
            listener.selectionOccurred(event);
        }
    }

    /** Selection made elsewhere (the site/link tables); mirror it here. */
    @Override
    public void selectionOccurred(MoleculeSelectionEvent event) {
        clearSelection();
        selectedSites.addAll(event.getSelectedSites());
        selectedLinks.addAll(event.getSelectedLinks());
        repaint();
    }

    /* ******************** Jmol rotation sync ******************** */

    public void setintListener(ActionListener al) {
        this.intListener = al;
    }

    /** Hand the current orientation to whoever is mirroring it, as the Java3D version did. */
    private void publishRotation() {
        Affine rot = new Affine();
        trackball.getMatrixGL(rot);
        // Affine keeps its elements package-private, so read the rotation by transforming the
        // basis vectors: M * e_i is column i of M.
        Vect3d cx = rot.mult(new Vect3d(1, 0, 0));
        Vect3d cy = rot.mult(new Vect3d(0, 1, 0));
        Vect3d cz = rot.mult(new Vect3d(0, 0, 1));
        m3 = new Matrix3f(
                (float) cx.getX(), (float) cy.getX(), (float) cz.getX(),
                (float) cx.getY(), (float) cy.getY(), (float) cz.getY(),
                (float) cx.getZ(), (float) cy.getZ(), (float) cz.getZ());
        if (intListener != null) {
            intListener.actionPerformed(new RotationUpdateEvent(m3, false));
        }
    }

    @Override
    public void rotationOccurred(RotationUpdateEvent event) {
        this.m3 = event.getM3();
        if (event.notifyPanel()) {
            notifyListeners();
        }
    }

    /* ******************** molecule mutations ******************** */

    /** Kept for callers; the scene is derived from the molecule, so this only needs a repaint. */
    public void addSite(Site site) { repaint(); }

    public void addLink(Link link) { repaint(); }

    public void removeLink(Link link) {
        selectedLinks.remove(link);
        repaint();
    }

    public void shiftSites(double dx, double dy, double dz) {
        for (Site site : selectedSites) {
            site.setX(site.getX() + dx);
            site.setY(site.getY() + dy);
            if (!site.getTypeName().equals(SiteType.ANCHOR)) {
                site.setZ(site.getZ() + dz);
            }
        }
        notifyListeners();
        repaint();
    }

    public void addLinkToMol(Link link) {
        molecule.getLinkArray().add(link);
        notifyListeners();
        repaint();
    }

    public void removeLinkToMol(Link link) {
        molecule.getLinkArray().remove(link.getIndex());
        selectedLinks.remove(link);
        notifyListeners();
        repaint();
    }

    /**
     * Set a new radius on the site <em>type</em> of every selected site — so every site sharing
     * that type changes with it, which is what the type-based model means.
     */
    public void updateRadius(ArrayList<Site> sites, Double newRadius) {
        for (Site s : new ArrayList<>(sites)) {
            if (s.getType() != null) {
                s.getType().setRadius(newRadius);
            }
        }
        notifyListeners();
        repaint();
    }

    /**
     * Sites whose spheres intersect, mapped to how many neighbours each overlaps. The editor warns
     * on close so the user can fix a molecule the solver would reject. Unchanged from the Java3D
     * version -- it never touched the scene graph.
     */
    public HashMap<Site, Integer> getOverlaps() {
        HashMap<Site, Integer> overlaps = new HashMap<>();
        List<Site> sites = molecule.getSiteArray();
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                Site s1 = sites.get(i), s2 = sites.get(j);
                if (s1.getRadius() + s2.getRadius() >= distance(s1, s2)) {
                    overlaps.merge(s1, 1, Integer::sum);
                    overlaps.merge(s2, 1, Integer::sum);
                }
            }
        }
        return overlaps;
    }

    private static double distance(Site s1, Site s2) {
        double dx = s1.getX() - s2.getX(), dy = s1.getY() - s2.getY(), dz = s1.getZ() - s2.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Mirror the molecule through a plane: {@code 0} flips x, {@code 1} y, {@code 2} z. Anchor
     * sites are left alone -- they are pinned to the membrane and flipping one would move it off.
     */
    public void flip(int axis) {
        for (Site s : molecule.getSiteArray()) {
            if (s.getTypeName().equals(SiteType.ANCHOR)) {
                continue;
            }
            switch (axis) {
                case 0 -> s.setX(-s.getX());
                case 1 -> s.setY(-s.getY());
                case 2 -> s.setZ(-s.getZ());
                default -> throw new IllegalArgumentException("axis must be 0, 1 or 2: " + axis);
            }
        }
        notifyListeners();
        repaint();
    }

    /* ******************** keyboard ******************** */

    @Override public void keyTyped(KeyEvent e) { }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_CONTROL || e.getKeyCode() == KeyEvent.VK_META) {
            ctrlPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_CONTROL || e.getKeyCode() == KeyEvent.VK_META) {
            ctrlPressed = false;
        }
    }
}
