package org.springsalad.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization of the solver's viewer ("trajectory") file -- the {@code _VIEW_Run0.txt} that
 * {@code viewer_files/} holds and {@link ViewerGUI} renders.
 *
 * <p>This format is a three-way contract: the LangevinNoVis01 solver writes it, this project reads
 * it, and VCell reads it too with an independently written parser. Nothing here asserts anything
 * about <em>our</em> renderer -- it pins the file grammar itself, so that a change to the format
 * shows up as a test failure rather than as two silently diverging viewers.
 *
 * <p>Fixtures are trimmed from this project's own solver output (Simulation12, 8 sites, first and
 * last frame). {@code example_files/example_SIMULATIONS/} is gitignored, so tests cannot read it.
 */
class ViewerTrajectoryFormatTest {

    private static final String TRAJECTORY = "/viewer/example_VIEW_Run0.txt";
    private static final String SITE_IDS = "/viewer/example_SiteIDs.csv";

    private static List<String> lines(String resource) throws IOException {
        try (InputStream in = ViewerTrajectoryFormatTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing test resource " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    @Test
    @DisplayName("header is tab-delimited key/value and carries the six keys consumers read")
    void headerKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : lines(TRAJECTORY)) {
            if (line.isBlank()) {
                break; // a blank line ends the header
            }
            String[] t = line.split("\t");
            assertEquals(2, t.length, "header line is not key<TAB>value: " + line);
            keys.add(t[0]);
            Double.parseDouble(t[1].trim()); // every header value is a double
        }
        assertEquals(Set.of("TotalTime", "dtimage", "xsize", "ysize", "z_outside", "z_inside"), keys);
    }

    @Test
    @DisplayName("body is SCENE blocks, each with a SceneNumber line")
    void sceneBlocks() throws IOException {
        List<String> body = lines(TRAJECTORY);
        int scenes = 0;
        int sceneNumbers = 0;
        for (String line : body) {
            if (line.equals("SCENE")) {
                scenes++;
            } else if (line.startsWith("SceneNumber")) {
                sceneNumbers++;
                String[] t = line.split("\t");
                assertEquals(4, t.length, "SceneNumber arity changed: " + line);
                assertEquals("CurrentTime", t[2]);
                Integer.parseInt(t[1].trim());
                Double.parseDouble(t[3].trim());
            }
        }
        assertTrue(scenes >= 2, "fixture should hold at least two frames");
        assertEquals(scenes, sceneNumbers, "every SCENE needs exactly one SceneNumber");
    }

    @Test
    @DisplayName("ID lines are: ID <id> <radius> <COLOR> <x> <y> <z>")
    void siteLineGrammar() throws IOException {
        int sites = 0;
        for (String line : lines(TRAJECTORY)) {
            if (!line.startsWith("ID\t")) {
                continue;
            }
            sites++;
            String[] t = line.split("\t");
            assertEquals(7, t.length, "ID arity changed: " + line);
            Integer.parseInt(t[1].trim());
            Double.parseDouble(t[2].trim());
            assertFalse(t[3].isBlank(), "colour name missing: " + line);
            Double.parseDouble(t[4].trim());
            Double.parseDouble(t[5].trim());
            Double.parseDouble(t[6].trim());
        }
        assertTrue(sites > 0, "no ID lines in fixture");
    }

    @Test
    @DisplayName("Link lines are: Link <idA> : <idB>, and reference sites present in the frame")
    void linkLineGrammar() throws IOException {
        Set<String> idsInFrame = new LinkedHashSet<>();
        List<String> offenders = new ArrayList<>();
        int links = 0;
        for (String line : lines(TRAJECTORY)) {
            if (line.equals("SCENE")) {
                idsInFrame.clear();
            } else if (line.startsWith("ID\t")) {
                idsInFrame.add(line.split("\t")[1].trim());
            } else if (line.startsWith("Link\t")) {
                links++;
                String[] t = line.split("\t");
                assertEquals(4, t.length, "Link arity changed: " + line);
                assertEquals(":", t[2].trim(), "Link separator changed: " + line);
                if (!idsInFrame.contains(t[1].trim()) || !idsInFrame.contains(t[3].trim())) {
                    offenders.add(line);
                }
            }
        }
        assertTrue(links > 0, "no Link lines in fixture");
        assertEquals(List.of(), offenders, "links reference sites not listed in their own frame");
    }

    @Test
    @DisplayName("colour names in the trajectory are names this project knows")
    void coloursAreKnownNames() throws IOException {
        Set<String> known = Set.of(org.springsalad.helpersetup.Colors.COLORNAMES);
        Set<String> seen = new LinkedHashSet<>();
        for (String line : lines(TRAJECTORY)) {
            if (line.startsWith("ID\t")) {
                seen.add(line.split("\t")[3].trim());
            }
        }
        assertFalse(seen.isEmpty());
        for (String colour : seen) {
            assertTrue(known.contains(colour),
                    "trajectory uses colour '" + colour + "' that Colors.COLORNAMES does not define");
        }
    }

    @Test
    @DisplayName("SiteIDs.csv names every site: <id>,<Molecule> Site <n> SiteType <Type>")
    void siteIdentityGrammar() throws IOException {
        // Lives at <sim>_FOLDER/data/Run0/SiteIDs.csv -- NOT beside the trajectory in viewer_files/.
        // It is what lets a viewer group sites by real site type instead of by colour+radius.
        int parsed = 0;
        for (String line : lines(SITE_IDS)) {
            if (line.isBlank()) {
                continue;
            }
            assertTrue(line.matches("^\\s*\\d+\\s*,\\s*.+? Site \\d+ SiteType .+?\\s*$"),
                    "unrecognized SiteIDs line: " + line);
            parsed++;
        }
        assertTrue(parsed > 0, "no identities parsed");
    }

    @Test
    @DisplayName("without SiteIDs.csv, colour+radius collapses distinct site types into one")
    void colourAndRadiusAloneCannotSeparateSiteTypes() throws IOException {
        // Documents why a ported viewer must read SiteIDs.csv: in this model every site is
        // RED at radius 1.0, so a colour+radius fallback yields ONE visibility toggle for what
        // SiteIDs.csv correctly reports as two distinct site types.
        Set<String> colourRadius = new LinkedHashSet<>();
        for (String line : lines(TRAJECTORY)) {
            if (line.startsWith("ID\t")) {
                String[] t = line.split("\t");
                colourRadius.add(t[3].trim() + "@" + t[2].trim());
            }
        }
        Set<String> realTypes = new LinkedHashSet<>();
        for (String line : lines(SITE_IDS)) {
            if (!line.isBlank()) {
                realTypes.add(line.substring(line.indexOf(',') + 1).trim());
            }
        }
        assertEquals(1, colourRadius.size(), "fixture no longer demonstrates the collapse");
        assertEquals(2, realTypes.size(), "fixture should carry two real site types");
    }
}
