package org.springsalad.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Vect3d} is ported from VCell. The copy constructor is the one thing that differs:
 * upstream it is {@code Vect3d(ThreeSpacePoint)}, reached because {@code Vect3d} implemented that
 * interface. Severing the interface for this port would have silently removed {@link Camera}'s
 * copy constructor, so it is declared explicitly and pinned here.
 */
class Vect3dTest {

    private static void assertXyz(Vect3d v, double x, double y, double z) {
        assertEquals(x, v.getX(), 1e-12);
        assertEquals(y, v.getY(), 1e-12);
        assertEquals(z, v.getZ(), 1e-12);
    }

    @Test
    @DisplayName("copy constructor copies the components")
    void copyConstructorCopiesComponents() {
        Vect3d original = new Vect3d(1.5, -2.25, 3.0);
        assertXyz(new Vect3d(original), 1.5, -2.25, 3.0);
    }

    @Test
    @DisplayName("copy constructor makes an independent vector, not an alias")
    void copyConstructorIsDeep() {
        Vect3d original = new Vect3d(1, 2, 3);
        Vect3d copy = new Vect3d(original);
        assertNotSame(original, copy);
        original.set(9, 9, 9);
        assertXyz(copy, 1, 2, 3);
    }

    @Test
    @DisplayName("cross, dot and length behave as the renderer expects")
    void vectorMath() {
        Vect3d x = new Vect3d(1, 0, 0);
        Vect3d y = new Vect3d(0, 1, 0);
        assertXyz(x.cross(y), 0, 0, 1);
        assertEquals(0.0, x.dot(y), 1e-12);
        assertEquals(1.0, x.length(), 1e-12);
        assertEquals(5.0, new Vect3d(3, 4, 0).length(), 1e-12);
        assertEquals(25.0, new Vect3d(3, 4, 0).lengthSquared(), 1e-12);
    }

    @Test
    @DisplayName("add and sub, both instance and static forms")
    void addAndSub() {
        assertXyz(Vect3d.add(new Vect3d(1, 2, 3), new Vect3d(4, 5, 6)), 5, 7, 9);
        assertXyz(Vect3d.sub(new Vect3d(4, 5, 6), new Vect3d(1, 2, 3)), 3, 3, 3);
        Vect3d v = new Vect3d(1, 1, 1);
        v.add(new Vect3d(2, 3, 4));
        assertXyz(v, 3, 4, 5);
        v.sub(new Vect3d(1, 1, 1));
        assertXyz(v, 2, 3, 4);
    }

    @Test
    @DisplayName("unit normalizes in place; scale and uminus")
    void unitScaleNegate() {
        Vect3d v = new Vect3d(0, 3, 4);
        v.unit();
        assertEquals(1.0, v.length(), 1e-12);
        assertXyz(v, 0, 0.6, 0.8);

        Vect3d s = new Vect3d(1, 2, 3);
        s.scale(2);
        assertXyz(s, 2, 4, 6);
        assertXyz(s.uminus(), -2, -4, -6);
    }

    @Test
    @DisplayName("zero and equals")
    void zeroAndEquals() {
        Vect3d v = new Vect3d(1, 2, 3);
        v.zero();
        assertXyz(v, 0, 0, 0);
        assertTrue(new Vect3d(1, 2, 3).equals(new Vect3d(1, 2, 3)));
    }
}
