package org.springsalad.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which way the scene turns when the user drags.
 *
 * <p>These exist because {@code projectToSphere_xy} used to return the grab point on the BACK
 * hemisphere ({@code -z}), which mirrors the rotation axis for every drag: drag right and the
 * scene turned left. A test that only checks "the image changed when I dragged" cannot see that;
 * these check the direction. Projection here goes through the same accessor the canvas uses, so
 * changing either the canvas's accessor or the sphere projection breaks them.
 *
 * <p>The convention the viewer needs: the user grabs the front of the scene, so the point facing
 * the camera follows the mouse, and the side that swings toward the mouse goes <em>away</em> from
 * the camera.
 */
class TrackballDirectionTest {

    /** Where a world point lands, using the same accessor the canvas projects with. */
    private static Vect3d project(Trackball tb, double x, double y, double z) {
        Affine rot = new Affine();
        tb.getMatrixGL(rot);   // the accessor SpringSaladViewerCanvas.project uses
        return rot.mult(new Vect3d(x, y, z));
    }

    private static Trackball identityTrackball() {
        Trackball tb = new Trackball(new Camera(), Trackball.Handedness.RIGHT_HANDED);
        tb.setRotation(0, 0, 0); // look straight down -z; +x right, +y up, +z toward the camera
        return tb;
    }

    @Test
    @DisplayName("dragging right turns the front of the scene right, not left")
    void dragRightTurnsRight() {
        Trackball tb = identityTrackball();
        double before = project(tb, 0, 0, 1).getX();
        tb.rotate_xy(0.0, 0.0, 0.30, 0.0);   // centre -> right
        double after = project(tb, 0, 0, 1).getX();
        assertTrue(after > before,
                "front of the scene moved " + (after - before) + " on a rightward drag; "
                        + "it must follow the mouse, not run from it");
    }

    @Test
    @DisplayName("dragging right swings the right-hand side away from the camera")
    void dragRightPushesRightSideBack() {
        Trackball tb = identityTrackball();
        double before = project(tb, 1, 0, 0).getZ();
        tb.rotate_xy(0.0, 0.0, 0.30, 0.0);
        double after = project(tb, 1, 0, 0).getZ();
        assertTrue(after < before,
                "the right-hand side came toward the camera (depth " + (after - before)
                        + ") on a rightward drag; the scene is turning backwards");
    }

    @Test
    @DisplayName("dragging left is the mirror of dragging right")
    void dragLeftMirrorsDragRight() {
        Trackball right = identityTrackball();
        right.rotate_xy(0.0, 0.0, 0.30, 0.0);
        Trackball left = identityTrackball();
        left.rotate_xy(0.0, 0.0, -0.30, 0.0);
        assertTrue(project(right, 0, 0, 1).getX() > 0 && project(left, 0, 0, 1).getX() < 0,
                "left and right drags must move the front marker to opposite sides");
    }

    @Test
    @DisplayName("dragging up tips the top of the scene toward the camera")
    void dragUpTipsTopForward() {
        // Same convention on the other axis: grab the front and pull down/up, the front follows.
        Trackball tb = identityTrackball();
        double before = project(tb, 0, 0, 1).getY();
        tb.rotate_xy(0.0, 0.0, 0.0, 0.30);   // centre -> up (canvas passes y already flipped, y-up)
        double after = project(tb, 0, 0, 1).getY();
        assertTrue(after > before,
                "front of the scene moved " + (after - before) + " vertically on an upward drag");
    }

    // ---- axis purity: a straight drag must not roll the scene ----

    /** How far a view-space basis vector moves under the rotation a drag produced. */
    private static double viewAxisMotion(double x1, double y1, double x2, double y2, Vect3d axis) {
        Trackball tb = identityTrackball();
        tb.setRotation(Math.toRadians(35 - 90), Math.toRadians(30), 0); // the canvas's default view
        Affine m = new Affine(), mInv = new Affine();
        tb.getMatrixGL(m);
        tb.getInvMatrixGL(mInv);
        tb.rotate_xy(x1, y1, x2, y2);
        Affine m2 = new Affine();
        tb.getMatrixGL(m2);
        Vect3d moved = m2.mult(mInv.mult(axis));           // view -> world -> view again
        return Math.sqrt(Math.pow(moved.getX() - axis.getX(), 2)
                + Math.pow(moved.getY() - axis.getY(), 2)
                + Math.pow(moved.getZ() - axis.getZ(), 2));
    }

    @Test
    @DisplayName("a horizontal drag spins about the screen-vertical axis, with no roll")
    void horizontalDragDoesNotRoll() {
        assertTrue(viewAxisMotion(0.0, 0.0, 0.20, 0.0, new Vect3d(0, 1, 0)) < 1e-9,
                "a horizontal drag moved the view's vertical axis: the scene is rolling");
    }

    @Test
    @DisplayName("no roll even out on the hyperbolic sheet, away from the ball centre")
    void horizontalDragDoesNotRollOffCentre() {
        // Beyond TRACKBALLSIZE/sqrt(2) the grab point leaves the sphere for the hyperbolic sheet.
        assertTrue(viewAxisMotion(0.5, 0.0, 0.70, 0.0, new Vect3d(0, 1, 0)) < 1e-9,
                "a horizontal drag in the hyperbolic region rolled the scene");
    }

    @Test
    @DisplayName("a vertical drag spins about the screen-horizontal axis")
    void verticalDragDoesNotRoll() {
        assertTrue(viewAxisMotion(0.0, 0.0, 0.0, 0.20, new Vect3d(1, 0, 0)) < 1e-9,
                "a vertical drag moved the view's horizontal axis: the scene is rolling");
    }

    // ---- handedness: the setting must actually switch which face the drag grabs ----

    private static Vect3d seenBy(Trackball tb, Vect3d world) {
        Affine m = new Affine();
        tb.getMatrixGL(m);
        return m.mult(world);
    }

    private static Trackball trackball(Trackball.Handedness handedness) {
        Trackball tb = new Trackball(new Camera(), handedness);
        tb.setRotation(0, 0, 0);
        return tb;
    }

    @Test
    @DisplayName("LEFT_HANDED grabs the opposite face, so the same drag turns the other way")
    void handednessSwitchesTheGrabbedFace() {
        // Mirrors VCell's TrackballHandednessTest. This viewer is right-handed (+z toward the
        // camera); the geometry viewers upstream are left-handed. Asking for the wrong one turns
        // the scene backwards on both axes and looks fine in a still image.
        Vect3d front = new Vect3d(0, 0, 1);

        Trackball right = trackball(Trackball.Handedness.RIGHT_HANDED);
        double rx0 = seenBy(right, front).getX();
        right.rotate_xy(0.0, 0.0, 0.30, 0.0);
        double rightDx = seenBy(right, front).getX() - rx0;

        Trackball left = trackball(Trackball.Handedness.LEFT_HANDED);
        double lx0 = seenBy(left, front).getX();
        left.rotate_xy(0.0, 0.0, 0.30, 0.0);
        double leftDx = seenBy(left, front).getX() - lx0;

        assertTrue(rightDx > 0, "right-handed: +z should follow the mouse");
        assertTrue(leftDx < 0, "left-handed: +z should be carried the other way");
    }

    @Test
    @DisplayName("the canvas asks for the handedness it actually draws with")
    void canvasDeclaresRightHanded() {
        // The canvas projects with +z toward the camera and shades "nearer = brighter" off the
        // same axis, so RIGHT_HANDED is the only consistent choice. There is no default on the
        // constructor precisely so this cannot be left unconsidered.
        assertTrue(new SpringSaladViewerCanvasProbe().isRightHanded(),
                "SpringSaladViewerCanvas must construct its Trackball as RIGHT_HANDED");
    }

    /** Reads the canvas source rather than its private field; cheap and needs no API change. */
    private static final class SpringSaladViewerCanvasProbe {
        boolean isRightHanded() {
            java.nio.file.Path direct = java.nio.file.Paths.get(
                    "src/main/java/org/springsalad/viewer/SpringSaladViewerCanvas.java");
            java.nio.file.Path path = java.nio.file.Files.exists(direct)
                    ? direct : java.nio.file.Paths.get("..").resolve(direct);
            try {
                String src = java.nio.file.Files.readString(path);
                return src.contains("Trackball.Handedness.RIGHT_HANDED");
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        }
    }
}
