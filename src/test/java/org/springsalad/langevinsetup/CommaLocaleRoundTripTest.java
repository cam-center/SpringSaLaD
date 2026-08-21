package org.springsalad.langevinsetup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model file must be byte-identical whatever locale the user's machine runs in.
 *
 * <p>It is read by the LangevinNoVis01 solver with {@code Double.parseDouble}, which only accepts
 * '.'. Before this was fixed, a user in Germany, France, Spain, Portugal or Russia got
 * {@code D 1,50000} written into their model and a solver that could not run it -- with no error
 * at save time, because the GUI could read its own commas back.
 */
class CommaLocaleRoundTripTest {

    private static final Locale COMMA = Locale.GERMANY;

    @TempDir
    Path tmp;

    private Locale previous;
    private Path working;

    private static Path exampleModel() {
        Path direct = Paths.get("example_files/example.txt");
        return Files.exists(direct) ? direct : Paths.get("..").resolve(direct);
    }

    @BeforeEach
    void useCommaLocale() throws IOException {
        previous = Locale.getDefault();
        Locale.setDefault(COMMA);
        working = tmp.resolve("example.txt");
        Files.copy(exampleModel(), working);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previous);
    }

    private Global load() {
        Global g = new Global("test");
        g.setFile(working.toFile());
        g.loadFile();
        return g;
    }

    @Test
    @DisplayName("no comma decimal separator reaches the model file")
    void writesNoCommaDecimals() throws IOException {
        Global g = load();
        g.writeFile();
        for (String line : Files.readString(working).split("\n")) {
            // Any digit,digit pair is a decimal comma; the format itself uses no comma anywhere.
            assertFalse(line.matches(".*\\d,\\d.*"),
                    "comma decimal written under locale " + COMMA + ": " + line);
        }
    }

    @Test
    @DisplayName("the file written under a comma locale is identical to the dot-locale one")
    void writesSameBytesAsDotLocale() throws IOException {
        Global underComma = load();
        underComma.writeFile();
        String commaOutput = Files.readString(working);

        Locale.setDefault(Locale.US);
        Path other = tmp.resolve("dot.txt");
        Files.copy(exampleModel(), other);
        Global underDot = new Global("test");
        underDot.setFile(other.toFile());
        underDot.loadFile();
        underDot.writeFile();

        assertEquals(Files.readString(other), commaOutput,
                "the same model saved on a German and a US machine produced different files");
    }

    @Test
    @DisplayName("values survive a full round trip under a comma locale")
    void valuesSurviveRoundTrip() {
        Global before = load();
        double x = before.getBoxGeometry().getX();
        double totalTime = before.getSystemTimes().getTotalTime();
        double dt = before.getSystemTimes().getdt();
        int molecules = before.getMolecules().size();
        before.writeFile();

        Global after = load();
        assertEquals(x, after.getBoxGeometry().getX(), 1e-12);
        assertEquals(totalTime, after.getSystemTimes().getTotalTime(), 1e-15);
        assertEquals(dt, after.getSystemTimes().getdt(), 1e-15);
        assertEquals(molecules, after.getMolecules().size());
        assertTrue(x > 0 && totalTime > 0, "fixture should carry non-zero values");
    }

    @Test
    @DisplayName("a fractional diffusion coefficient is not mangled by the locale")
    void fractionalValuesSurvive() {
        // The value that motivated 4f05dc5, through the write/read path under a comma locale.
        SiteType t = new SiteType(null, "Site0");
        t.setD(0.12345);
        t.setRadius(2.5);
        java.io.StringWriter sw = new java.io.StringWriter();
        try (java.io.PrintWriter p = new java.io.PrintWriter(sw)) {
            t.writeType(p);
        }
        String line = sw.toString().trim();
        assertTrue(line.contains(" D 0.12345 "), "wrote: " + line);
        SiteType reloaded = SiteType.readType(null, line);
        assertEquals(0.12345, reloaded.getD(), 1e-12);
        assertEquals(2.5, reloaded.getRadius(), 1e-12);
    }
}
