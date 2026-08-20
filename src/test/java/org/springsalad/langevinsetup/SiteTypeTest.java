package org.springsalad.langevinsetup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SiteType#writeType} emits the {@code TYPE:} line of the model file, which the
 * LangevinNoVis01 solver parses. Its DF indices decide how much of the user's input survives.
 */
class SiteTypeTest {

    /** The molecule back-reference is only read by getMoleculeName(); write/read do not touch it. */
    private static SiteType type(String name) {
        return new SiteType(null, name);
    }

    private static String write(SiteType t) {
        StringWriter sw = new StringWriter();
        try (PrintWriter p = new PrintWriter(sw)) {
            t.writeType(p);
        }
        return sw.toString().trim();
    }

    @Test
    @DisplayName("REGRESSION 4f05dc5: diffusion coefficient keeps 5 decimals, not 3")
    void diffusionCoefficientKeepsFiveDecimals() {
        // Before 4f05dc5 this was DF[3] and 0.00012 was written as "0.000" -- a silent,
        // unrecoverable loss of the user's diffusion rate on the way to the solver.
        SiteType t = type("Site0");
        t.setD(0.00012);
        assertTrue(write(t).contains(" D 0.00012 "),
                "expected 5-decimal D, got: " + write(t));
    }

    @Test
    @DisplayName("radius keeps 5 decimals")
    void radiusKeepsFiveDecimals() {
        SiteType t = type("Site0");
        t.setRadius(1.23456);
        assertTrue(write(t).contains(" Radius 1.23456 "), "got: " + write(t));
    }

    @Test
    @DisplayName("writeType emits the exact grammar the solver parses")
    void writeTypeGrammar() {
        SiteType t = type("Site0");
        t.setRadius(1.0);
        t.setD(2.0);
        t.setColor("BLUE");
        assertEquals("TYPE: Name \"Site0\" Radius 1.00000 D 2.00000 Color BLUE STATES \"State0\"",
                write(t).replaceAll("\\s+$", ""));
    }

    @Test
    @DisplayName("readType(writeType(x)) preserves name, radius, D and colour")
    void writeThenReadRoundTrips() {
        SiteType original = type("My Site");
        original.setRadius(2.50000);
        original.setD(0.12345);
        original.setColor("GOLD");

        SiteType reloaded = SiteType.readType(null, write(original));

        assertEquals("My Site", reloaded.getName(), "quoted names with spaces must survive");
        assertEquals(2.5, reloaded.getRadius(), 1e-9);
        assertEquals(0.12345, reloaded.getD(), 1e-9);
        assertEquals("GOLD", reloaded.getColorName());
    }

    @Test
    @DisplayName("states survive the round trip")
    void statesRoundTrip() {
        SiteType original = type("Site1");
        original.addState(new State(original, "state1"));
        SiteType reloaded = SiteType.readType(null, write(original));
        assertEquals(original.getStates().size(), reloaded.getStates().size());
        assertEquals("state1", reloaded.getStates().get(1).toString());
    }
}
