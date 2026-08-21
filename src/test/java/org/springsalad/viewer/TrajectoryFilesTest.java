package org.springsalad.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the on-disk layout the viewer walks. It is easy to get wrong: the trajectory lives under
 * {@code viewer_files/} but the site identities live under {@code data/Run<N>/}, a different
 * subtree of the same simulation folder.
 */
class TrajectoryFilesTest {

    @TempDir
    Path tmp;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = TrajectoryFilesTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            return in.readAllBytes();
        }
    }

    /** Build a simulation folder as SimulationManager and the solver would lay it out. */
    private Path simulation(String name, int runs, boolean withSiteIds) throws IOException {
        Path simFile = tmp.resolve(name + "_SIM.txt");
        Files.writeString(simFile, "placeholder\n");
        Path folder = tmp.resolve(name + "_SIM_FOLDER");
        Files.createDirectories(folder.resolve("viewer_files"));
        for (int i = 0; i < runs; i++) {
            Files.write(folder.resolve("viewer_files/" + name + "_SIM_VIEW_Run" + i + ".txt"),
                    resource("/viewer/example_VIEW_Run0.txt"));
            if (withSiteIds) {
                Files.createDirectories(folder.resolve("data/Run" + i));
                Files.write(folder.resolve("data/Run" + i + "/SiteIDs.csv"),
                        resource("/viewer/example_SiteIDs.csv"));
            }
        }
        return simFile;
    }

    private static List<TrajectoryFiles.Run> runs(Path simFile) {
        return TrajectoryFiles.runsFor(new org.springsalad.runlauncher.Simulation(simFile.toFile()));
    }

    @Test
    @DisplayName("finds every _VIEW_Run<N>.txt, ordered by run number")
    void findsRunsInOrder() throws IOException {
        List<TrajectoryFiles.Run> found = runs(simulation("Simulation0", 3, true));
        assertEquals(3, found.size());
        assertEquals(List.of(0, 1, 2), found.stream().map(TrajectoryFiles.Run::getIndex).toList());
    }

    @Test
    @DisplayName("run numbers are read numerically, so Run10 sorts after Run9")
    void runsSortNumericallyNotLexically() throws IOException {
        List<TrajectoryFiles.Run> found = runs(simulation("Simulation0", 11, false));
        assertEquals(11, found.size());
        assertEquals(9, found.get(9).getIndex());
        assertEquals(10, found.get(10).getIndex(), "Run10 sorted lexically before Run9");
    }

    @Test
    @DisplayName("SiteIDs.csv is found under data/Run<N>/, not beside the trajectory")
    void locatesSiteIdentities() throws IOException {
        TrajectoryFiles.Run run = runs(simulation("Simulation0", 1, true)).get(0);
        assertTrue(run.hasSiteIdentities());
        SpringSaladTrajectory t = run.load();
        assertTrue(t.hasSiteIdentities());
        assertEquals("site:MT0\0Site1", t.siteTypeKey(t.getFrames().get(0).getSites().get(0)));
    }

    @Test
    @DisplayName("a run without SiteIDs.csv still loads, falling back to colour+radius")
    void loadsWithoutSiteIdentities() throws IOException {
        TrajectoryFiles.Run run = runs(simulation("Simulation0", 1, false)).get(0);
        assertFalse(run.hasSiteIdentities());
        SpringSaladTrajectory t = run.load();
        assertFalse(t.hasSiteIdentities());
        assertEquals(2, t.getFrameCount(), "trajectory should still parse");
    }

    @Test
    @DisplayName("a corrupt SiteIDs.csv costs the names, not the trajectory")
    void corruptSiteIdsDoesNotLoseTrajectory() throws IOException {
        Path simFile = simulation("Simulation0", 1, true);
        Files.writeString(tmp.resolve("Simulation0_SIM_FOLDER/data/Run0/SiteIDs.csv"),
                "]]] not a csv [[[\n", StandardCharsets.UTF_8);
        SpringSaladTrajectory t = runs(simFile).get(0).load();
        assertEquals(2, t.getFrameCount());
        assertFalse(t.hasSiteIdentities());
    }

    @Test
    @DisplayName("a malformed trajectory surfaces as IOException, not an unchecked throw")
    void malformedTrajectoryIsReportable() throws IOException {
        Path simFile = simulation("Simulation0", 1, false);
        Files.writeString(tmp.resolve("Simulation0_SIM_FOLDER/viewer_files/Simulation0_SIM_VIEW_Run0.txt"),
                "TotalTime\tnonsense\n\n");
        // The panel opens files the user chose; it must be able to catch and report the failure.
        assertThrows(IOException.class, () -> runs(simFile).get(0).load());
    }

    @Test
    @DisplayName("a simulation with no viewer_files yields no runs rather than throwing")
    void missingViewerFolderIsEmpty() throws IOException {
        Path simFile = tmp.resolve("Simulation9_SIM.txt");
        Files.writeString(simFile, "placeholder\n");
        assertEquals(List.of(), runs(simFile));
    }
}
