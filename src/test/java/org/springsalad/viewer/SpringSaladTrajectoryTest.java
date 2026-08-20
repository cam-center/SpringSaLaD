package org.springsalad.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpringSaladTrajectory} is ported verbatim from VCell. These tests are this project's
 * own: they pin the behaviour the viewer depends on, against fixtures produced by this project's
 * solver, so that the two copies cannot drift silently.
 */
class SpringSaladTrajectoryTest {

    private static Reader open(String resource) {
        InputStream in = SpringSaladTrajectoryTest.class.getResourceAsStream(resource);
        assertNotNull(in, "missing test resource " + resource);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private static SpringSaladTrajectory trajectory() throws IOException {
        try (Reader r = open("/viewer/example_VIEW_Run0.txt")) {
            return SpringSaladTrajectory.parse(r);
        }
    }

    @Test
    @DisplayName("header fields are read into the scene box dimensions")
    void headerParsed() throws IOException {
        SpringSaladTrajectory t = trajectory();
        assertEquals(0.01, t.getTotalTime(), 1e-12);
        assertEquals(1.0e-4, t.getDtImage(), 1e-12);
        assertEquals(50.0, t.getXSize(), 1e-9);
        assertEquals(50.0, t.getYSize(), 1e-9);
        assertEquals(10.000000000000009, t.getZOutside(), 1e-9);
        assertEquals(90.0, t.getZInside(), 1e-9);
    }

    @Test
    @DisplayName("frames, sites and links are read in file order")
    void framesParsed() throws IOException {
        SpringSaladTrajectory t = trajectory();
        assertEquals(2, t.getFrameCount());

        SpringSaladTrajectory.Frame first = t.getFrames().get(0);
        assertEquals(0, first.getSceneNumber());
        assertEquals(0.0, first.getTime(), 1e-12);
        assertEquals(8, first.getSites().size());
        assertEquals(4, first.getLinks().size());

        SpringSaladTrajectory.Frame last = t.getFrames().get(1);
        assertEquals(100, last.getSceneNumber());
        assertEquals(0.010000009999948592, last.getTime(), 1e-15);
        assertEquals(5, last.getLinks().size(), "a bond forms by the last frame");

        SpringSaladTrajectory.Site s = first.getSites().get(0);
        assertEquals(100000001, s.getId());
        assertEquals(1.0, s.getRadius(), 1e-9);
        assertEquals("RED", s.getColor());
        assertEquals(-13.524488, s.getX(), 1e-9);
        assertEquals(-33.285629, s.getY(), 1e-9);
        assertEquals(57.701201, s.getZ(), 1e-9);
    }

    @Test
    @DisplayName("links are id pairs, both endpoints present in their own frame")
    void linksReferenceSitesInFrame() throws IOException {
        SpringSaladTrajectory t = trajectory();
        for (SpringSaladTrajectory.Frame f : t.getFrames()) {
            Set<Integer> ids = new LinkedHashSet<>();
            f.getSites().forEach(site -> ids.add(site.getId()));
            for (int[] link : f.getLinks()) {
                assertEquals(2, link.length);
                assertTrue(ids.contains(link[0]), "link endpoint not in frame: " + link[0]);
                assertTrue(ids.contains(link[1]), "link endpoint not in frame: " + link[1]);
            }
        }
    }

    @Test
    @DisplayName("frames and sites are immutable to callers")
    void collectionsAreUnmodifiable() throws IOException {
        SpringSaladTrajectory t = trajectory();
        assertThrows(UnsupportedOperationException.class, () -> t.getFrames().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> t.getFrames().get(0).getSites().clear());
    }

    @Test
    @DisplayName("a file without TotalTime is rejected rather than silently parsed as empty")
    void rejectsNonTrajectoryFile() {
        // Well-formed key/value pairs, just not a trajectory: this reaches the TotalTime check.
        assertThrows(IOException.class,
                () -> SpringSaladTrajectory.parse(new StringReader("width\t3\nheight\t4\n\n")));
    }

    @Test
    @DisplayName("ROUGH EDGE: a non-numeric header value throws NumberFormatException, not IOException")
    void malformedHeaderValueThrowsUnchecked() {
        // parse() documents IOException, but header values go straight through Double.parseDouble.
        // Callers that open a user-chosen file must catch RuntimeException too, or pre-check.
        assertThrows(NumberFormatException.class,
                () -> SpringSaladTrajectory.parse(new StringReader("TotalTime\tnot-a-number\n\n")));
    }

    @Test
    @DisplayName("unknown header keys and stray lines are tolerated")
    void tolerantOfUnknownContent() throws IOException {
        String text = "TotalTime\t1.0\nsomeFutureKey\t7\n\nSCENE\nSceneNumber\t0\tCurrentTime\t0.0\n"
                + "ID\t1\t1.0\tRED\t0.0\t0.0\t0.0\nWhatIsThis\tfoo\n";
        SpringSaladTrajectory t = SpringSaladTrajectory.parse(new StringReader(text));
        assertEquals(1, t.getFrameCount());
        assertEquals(1, t.getFrames().get(0).getSites().size());
    }

    // ---- site identities: the difference between real site types and a colour+radius guess ----

    @Test
    @DisplayName("SiteIDs.csv resolves sites to molecule and site type")
    void siteIdentitiesParsed() throws IOException {
        Map<Integer, SpringSaladTrajectory.SiteIdentity> ids;
        try (Reader r = open("/viewer/example_SiteIDs.csv")) {
            ids = SpringSaladTrajectory.parseSiteIdentities(r);
        }
        assertEquals(8, ids.size());
        SpringSaladTrajectory.SiteIdentity identity = ids.get(100000001);
        assertNotNull(identity);
        assertEquals("MT0", identity.getMoleculeName());
        assertEquals(1, identity.getSiteIndex());
        assertEquals("Site1", identity.getSiteTypeName());
    }

    @Test
    @DisplayName("without SiteIDs.csv two real site types collapse into one visibility toggle")
    void withoutIdentitiesSiteTypesCollapse() throws IOException {
        // The reason the viewer reads SiteIDs.csv at all: in this model every site is RED at
        // radius 1.0, so the colour+radius fallback cannot tell Site0 from Site1.
        SpringSaladTrajectory bare = trajectory();
        assertFalse(bare.hasSiteIdentities());
        Set<String> keys = new LinkedHashSet<>();
        bare.getFrames().get(0).getSites().forEach(s -> keys.add(bare.siteTypeKey(s)));
        assertEquals(1, keys.size(), "fixture no longer demonstrates the collapse");
        assertEquals(Set.of("color:RED@1.00000"), keys);
    }

    @Test
    @DisplayName("with SiteIDs.csv the same trajectory yields both real site types")
    void withIdentitiesSiteTypesSeparate() throws IOException {
        Map<Integer, SpringSaladTrajectory.SiteIdentity> ids;
        try (Reader r = open("/viewer/example_SiteIDs.csv")) {
            ids = SpringSaladTrajectory.parseSiteIdentities(r);
        }
        SpringSaladTrajectory t = trajectory().withSiteIdentities(ids);
        assertTrue(t.hasSiteIdentities());
        Set<String> keys = new LinkedHashSet<>();
        t.getFrames().get(0).getSites().forEach(s -> keys.add(t.siteTypeKey(s)));
        assertEquals(Set.of("site:MT0 Site0", "site:MT0 Site1"), keys);
    }

    @Test
    @DisplayName("withSiteIdentities leaves the receiver unchanged")
    void withSiteIdentitiesIsCopyOnWrite() throws IOException {
        SpringSaladTrajectory bare = trajectory();
        SpringSaladTrajectory enriched = bare.withSiteIdentities(Map.of(
                100000001, new SpringSaladTrajectory.SiteIdentity("MT0", 1, "Site1")));
        assertFalse(bare.hasSiteIdentities(), "receiver was mutated");
        assertTrue(enriched.hasSiteIdentities());
        assertEquals(bare.getFrameCount(), enriched.getFrameCount());
    }
}
