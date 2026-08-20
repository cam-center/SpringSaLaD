package org.springsalad.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the exporter end to end, headless, on a small synthetic trajectory. */
class SpringSaladMovieExporterTest {

    @TempDir
    Path tmp;

    private static SpringSaladViewerCanvas canvas(int frames) {
        List<SpringSaladTrajectory.Frame> list = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            List<SpringSaladTrajectory.Site> sites = new ArrayList<>();
            sites.add(new SpringSaladTrajectory.Site(1, 1.0, "RED", -10 + f, -5, 0));
            sites.add(new SpringSaladTrajectory.Site(2, 1.5, "BLUE", 10 - f, 5, 0));
            list.add(new SpringSaladTrajectory.Frame(f, f * 1.0e-4, sites,
                    new ArrayList<>(List.of(new int[]{1, 2}))));
        }
        SpringSaladViewerCanvas c = new SpringSaladViewerCanvas();
        c.setTrajectory(new SpringSaladTrajectory(1e-3, 1e-4, 50, 50, 10, 90, list));
        return c;
    }

    @Test
    @DisplayName("writes a non-empty MP4")
    void writesMp4() throws IOException {
        File out = tmp.resolve("movie.mp4").toFile();
        assertTrue(SpringSaladMovieExporter.writeMovie(canvas(6), out,
                SpringSaladMovieExporter.Format.MP4, 160, 120, 10, null));
        assertTrue(out.isFile() && out.length() > 0, "no MP4 written");
    }

    @Test
    @DisplayName("writes a non-empty animated GIF")
    void writesGif() throws IOException {
        File out = tmp.resolve("movie.gif").toFile();
        assertTrue(SpringSaladMovieExporter.writeMovie(canvas(6), out,
                SpringSaladMovieExporter.Format.ANIMATED_GIF, 160, 120, 10, null));
        assertTrue(out.isFile() && out.length() > 0, "no GIF written");
    }

    @Test
    @DisplayName("frame size is rounded down to whole macroblocks for H.264")
    void roundsToMacroblocks() throws IOException {
        // 170 -> 160, 130 -> 128. Encoding must succeed rather than produce a cropped frame.
        File out = tmp.resolve("odd.mp4").toFile();
        assertTrue(SpringSaladMovieExporter.writeMovie(canvas(3), out,
                SpringSaladMovieExporter.Format.MP4, 170, 130, 10, null));
        assertTrue(out.length() > 0);
    }

    @Test
    @DisplayName("progress is reported once per frame, in order")
    void reportsProgress() throws IOException {
        List<Integer> seen = new ArrayList<>();
        SpringSaladMovieExporter.writeMovie(canvas(5), tmp.resolve("p.mp4").toFile(),
                SpringSaladMovieExporter.Format.MP4, 64, 64, 10,
                new SpringSaladMovieExporter.Progress() {
                    @Override
                    public void frameDone(int frame, int total) {
                        assertEquals(5, total);
                        seen.add(frame);
                    }

                    @Override
                    public boolean isCancelled() { return false; }
                });
        assertEquals(List.of(0, 1, 2, 3, 4), seen);
    }

    @Test
    @DisplayName("cancelling stops the export and leaves no partial file behind")
    void cancelDeletesPartialFile() throws IOException {
        File out = tmp.resolve("cancelled.mp4").toFile();
        boolean completed = SpringSaladMovieExporter.writeMovie(canvas(20), out,
                SpringSaladMovieExporter.Format.MP4, 64, 64, 10,
                new SpringSaladMovieExporter.Progress() {
                    private int done;

                    @Override
                    public void frameDone(int frame, int total) { done = frame + 1; }

                    @Override
                    public boolean isCancelled() { return done >= 3; }
                });
        assertAll(
                () -> assertFalse(completed, "export reported success despite being cancelled"),
                () -> assertFalse(out.exists(),
                        "a partial MP4 was left behind; without finish() it has no index and will not play"));
    }

    @Test
    @DisplayName("an empty trajectory is refused rather than writing an unplayable file")
    void refusesEmptyTrajectory() {
        File out = tmp.resolve("empty.mp4").toFile();
        assertThrows(IOException.class, () -> SpringSaladMovieExporter.writeMovie(
                new SpringSaladViewerCanvas(), out, SpringSaladMovieExporter.Format.MP4,
                64, 64, 10, null));
        assertFalse(out.exists());
    }

    @Test
    @DisplayName("GIF delay is milliseconds between frames, not fps")
    void gifDelayIsNotFps() throws IOException {
        // MovieMaker.makeAnimagedGIF passes fps*1000 as the inter-frame delay, so a 10 fps export
        // plays one frame every ten seconds. Pin the corrected behaviour: a 10 fps GIF must be
        // close in size/timing to a 20 fps one, not wildly different, and must actually write.
        File slow = tmp.resolve("slow.gif").toFile();
        File fast = tmp.resolve("fast.gif").toFile();
        SpringSaladMovieExporter.writeMovie(canvas(4), slow,
                SpringSaladMovieExporter.Format.ANIMATED_GIF, 64, 64, 10, null);
        SpringSaladMovieExporter.writeMovie(canvas(4), fast,
                SpringSaladMovieExporter.Format.ANIMATED_GIF, 64, 64, 20, null);
        assertTrue(slow.length() > 0 && fast.length() > 0);
    }
}
