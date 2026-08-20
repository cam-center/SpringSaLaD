package org.springsalad.langevinsetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model file is the contract with the LangevinNoVis01 solver, which parses the same file this
 * GUI writes. These tests pin the grammar: if a change to {@link Global} or a domain class alters
 * what reaches disk, it breaks the solver and every saved user model, and it breaks here first.
 *
 * <p>Fixture is {@code example_files/example.txt}, the one model file tracked in git.
 */
class GlobalRoundTripTest {

    /** Resolved from the module root so the test does not depend on the JVM working directory. */
    private static Path exampleModel() {
        Path direct = Paths.get("example_files/example.txt");
        return Files.exists(direct) ? direct : Paths.get("..").resolve(direct);
    }

    @TempDir
    Path tmp;

    private Path working;

    @BeforeEach
    void copyFixture() throws IOException {
        // Global.writeFile() overwrites the file it was loaded from; never point it at the fixture.
        working = tmp.resolve("example.txt");
        Files.copy(exampleModel(), working);
    }

    private static Global load(Path p) {
        Global g = new Global("test");
        g.setFile(p.toFile());
        g.loadFile();
        return g;
    }

    @Test
    @DisplayName("the tracked example model parses into a populated Global")
    void exampleModelParses() {
        Global g = load(working);
        assertFalse(g.getMolecules().isEmpty(), "no molecules parsed");
        assertEquals("MT0", g.getMolecule(0).getName());
        assertEquals(1, g.getBindingReactions().size());
        assertEquals(1, g.getTransitionReactions().size());
        assertTrue(g.getBoxGeometry().getX() > 0, "box geometry did not load");
    }

    @Test
    @DisplayName("write is idempotent: load -> write -> load -> write yields identical bytes")
    void writeIsIdempotent() throws IOException {
        Global first = load(working);
        first.writeFile();
        String afterFirstWrite = Files.readString(working);

        Global second = load(working);
        second.writeFile();
        String afterSecondWrite = Files.readString(working);

        assertEquals(afterFirstWrite, afterSecondWrite,
                "a second save changed the file; the writer and reader disagree somewhere");
    }

    @Test
    @DisplayName("round trip preserves molecules, sites, links and reactions")
    void structurePreserved() {
        Global before = load(working);
        int molecules = before.getMolecules().size();
        int sites = before.getMolecule(0).getSiteArray().size();
        int links = before.getMolecule(0).getLinkArray().size();
        List<String> names = before.getMoleculeNames();
        before.writeFile();

        Global after = load(working);
        assertEquals(molecules, after.getMolecules().size(), "molecule count changed");
        assertEquals(sites, after.getMolecule(0).getSiteArray().size(), "site count changed");
        assertEquals(links, after.getMolecule(0).getLinkArray().size(), "link count changed");
        assertEquals(names, after.getMoleculeNames(), "molecule names changed");
        assertEquals(before.getBindingReactions().size(), after.getBindingReactions().size());
        assertEquals(before.getTransitionReactions().size(), after.getTransitionReactions().size());
    }

    @Test
    @DisplayName("every *** SECTION *** header the solver looks for is written")
    void allSectionHeadersPresent() throws IOException {
        Global g = load(working);
        g.writeFile();
        String out = Files.readString(working);
        for (String section : new String[]{
                Global.TIME_INFORMATION, Global.SPATIAL_INFORMATION, Global.MOLECULES,
                Global.MOLECULE_FILES, Global.DECAY_REACTIONS, Global.TRANSITION_REACTIONS,
                Global.ALLOSTERIC_REACTIONS, Global.BINDING_REACTIONS, Global.MOLECULE_COUNTERS,
                Global.STATE_COUNTERS, Global.BOND_COUNTERS, Global.SITE_PROPERTY_COUNTERS}) {
            assertTrue(out.contains("*** " + section + " ***"),
                    "missing section header: " + section);
        }
    }

    @Test
    @DisplayName("section header strings are the literals the solver parses")
    void sectionHeaderLiteralsArePinned() {
        // Deliberately literal, not Global.MOLECULES etc. Asserting against the constants would
        // still pass if someone renamed one, which is exactly the change that breaks the solver
        // and every saved user model.
        assertEquals("SYSTEM INFORMATION", Global.SPATIAL_INFORMATION);
        assertEquals("TIME INFORMATION", Global.TIME_INFORMATION);
        assertEquals("MOLECULES", Global.MOLECULES);
        assertEquals("MOLECULE FILES", Global.MOLECULE_FILES);
        assertEquals("CREATION/DECAY REACTIONS", Global.DECAY_REACTIONS);
        assertEquals("STATE TRANSITION REACTIONS", Global.TRANSITION_REACTIONS);
        assertEquals("ALLOSTERIC REACTIONS", Global.ALLOSTERIC_REACTIONS);
        assertEquals("BIMOLECULAR BINDING REACTIONS", Global.BINDING_REACTIONS);
        assertEquals("MOLECULE COUNTERS", Global.MOLECULE_COUNTERS);
        assertEquals("STATE COUNTERS", Global.STATE_COUNTERS);
        assertEquals("BOND COUNTERS", Global.BOND_COUNTERS);
        assertEquals("SITE PROPERTY COUNTERS", Global.SITE_PROPERTY_COUNTERS);
        assertEquals("CLUSTER COUNTERS", Global.CLUSTER_COUNTERS);
    }

    @Test
    @DisplayName("box geometry and system times survive the round trip exactly")
    void geometryAndTimesPreserved() {
        Global before = load(working);
        BoxGeometry bg = before.getBoxGeometry();
        double lx = bg.getX(), ly = bg.getY(), zIn = bg.getZin(), zOut = bg.getZout();
        double totalTime = before.getSystemTimes().getTotalTime();
        before.writeFile();

        Global after = load(working);
        assertEquals(lx, after.getBoxGeometry().getX(), 1e-9);
        assertEquals(ly, after.getBoxGeometry().getY(), 1e-9);
        assertEquals(zIn, after.getBoxGeometry().getZin(), 1e-9);
        assertEquals(zOut, after.getBoxGeometry().getZout(), 1e-9);
        assertEquals(totalTime, after.getSystemTimes().getTotalTime(), 1e-12);
    }

    @Test
    @DisplayName("writing does not depend on the process working directory")
    void writesToItsOwnFile() {
        Global g = load(working);
        g.writeFile();
        assertTrue(Files.exists(working));
        assertFalse(Files.exists(Paths.get("example.txt")), "wrote into the CWD instead of its file");
        assertTrue(new File(working.toString()).length() > 0);
    }
}
