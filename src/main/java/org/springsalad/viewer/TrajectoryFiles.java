package org.springsalad.viewer;

import org.springsalad.runlauncher.Simulation;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a simulation's trajectory files live, and how to load one.
 *
 * <p>For a simulation saved as {@code Simulation0_SIM.txt}:
 * <pre>
 * Simulation0_SIM_FOLDER/
 *   viewer_files/Simulation0_SIM_VIEW_Run&lt;N&gt;.txt   the trajectory, one per run
 *   data/Run&lt;N&gt;/SiteIDs.csv                       what each site id actually is
 * </pre>
 *
 * <p>The two are in different subtrees, which is easy to miss: {@code SiteIDs.csv} is <em>not</em>
 * beside the trajectory. Without it the viewer can only group sites by colour and radius, and two
 * site types that share both collapse into a single visibility toggle -- which is exactly what
 * happens on the shipped example, where every site is RED at radius 1.0.
 *
 * <p>Runs from before the solver wrote {@code SiteIDs.csv} simply have no such file; loading falls
 * back to the colour+radius grouping rather than failing.
 */
public final class TrajectoryFiles {

    private static final Pattern RUN_FILE = Pattern.compile("_VIEW_Run(\\d+)\\.txt$");

    private TrajectoryFiles() {
    }

    /** One entry per {@code _VIEW_Run<N>.txt} found, ordered by run number. */
    public static List<Run> runsFor(Simulation simulation) {
        File simFile = simulation.getFile();
        String name = simFile.getName();
        if (name.endsWith(".txt")) {
            name = name.substring(0, name.length() - 4);
        }
        File folder = new File(simFile.getParentFile(), name + "_FOLDER");
        File viewerFolder = new File(folder, "viewer_files");
        File[] files = viewerFolder.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<Run> runs = new ArrayList<>();
        for (File file : files) {
            Matcher m = RUN_FILE.matcher(file.getName());
            if (m.find()) {
                int index = Integer.parseInt(m.group(1));
                runs.add(new Run(index, file, new File(folder, "data/Run" + index + "/SiteIDs.csv")));
            }
        }
        runs.sort((a, b) -> Integer.compare(a.index, b.index));
        return runs;
    }

    /** A trajectory file and the site-identity file for the same run, which may not exist. */
    public static final class Run {
        private final int index;
        private final File trajectory;
        private final File siteIds;

        Run(int index, File trajectory, File siteIds) {
            this.index = index;
            this.trajectory = trajectory;
            this.siteIds = siteIds;
        }

        public int getIndex() { return index; }
        public File getTrajectoryFile() { return trajectory; }

        /** True when this run recorded what its sites are, i.e. site types can be named. */
        public boolean hasSiteIdentities() { return siteIds.isFile(); }

        /**
         * Parse the trajectory, attaching site identities when the run recorded them.
         *
         * @throws IOException if the trajectory cannot be read or is not a trajectory file
         */
        public SpringSaladTrajectory load() throws IOException {
            SpringSaladTrajectory t;
            try (Reader r = reader(trajectory.toPath())) {
                t = SpringSaladTrajectory.parse(r);
            } catch (RuntimeException e) {
                // parse() lets a malformed header value out as NumberFormatException; the caller
                // asked to open a file, so give it the failure it can actually report.
                throw new IOException("could not read " + trajectory.getName() + ": " + e, e);
            }
            if (!siteIds.isFile()) {
                return t;
            }
            try (Reader r = reader(siteIds.toPath())) {
                Map<Integer, SpringSaladTrajectory.SiteIdentity> ids =
                        SpringSaladTrajectory.parseSiteIdentities(r);
                return t.withSiteIdentities(ids);
            } catch (IOException | RuntimeException e) {
                // Identities are an enhancement; a broken SiteIDs.csv must not cost the user the
                // trajectory itself. They lose named site types, not the movie.
                e.printStackTrace(System.out);
                return t;
            }
        }

        private static Reader reader(Path path) throws IOException {
            return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return "Run " + index;
        }
    }
}
