# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

SpringSaLaD is a Java 17 / Swing desktop application for building, running, and analyzing
spatial, particle-based stochastic biochemical simulations (Langevin dynamics with excluded
volume). **This repo is the GUI/front-end only** — the numerical solver lives in a separate
project, [cam-center/LangevinNoVis01](https://github.com/cam-center/LangevinNoVis01), and is
consumed here as a prebuilt native executable.

MIT licensed; released versions are archived to Zenodo and citable via `CITATION.cff`.

## Build and run

```bash
mvn clean install dependency:copy-dependencies   # compile + fetch deps + download solvers
./run.sh                                         # launch the GUI from the repo root
```

`run.sh` runs `org.springsalad.langevinsetup.MainGUI` with
`--add-exports java.desktop/sun.awt=ALL-UNNAMED` on a classpath of
`target/dependency/*` plus `target/springsalad-0.0.1-SNAPSHOT.jar`. That `--add-exports` flag
is required (Java3D/AWT internals) and is also set in the install4j launcher config — if you
run the app another way, carry it over.

`dependency:copy-dependencies` is not optional: `target/dependency` must be populated or the
app won't start.

**Always launch from the repo root.** `RunLauncher.getSolverExecutable()` resolves the solver
by *relative* path (`./localsolvers/<platform>/langevin_*`), so the working directory must be
the directory containing `localsolvers/`.

### Solver binaries

OS-activated Maven profiles (`macprofile` / `winprofile` / `unixprofile`) each download **all
four** platform solvers into `localsolvers/{linux64,win64,macos_x86_64,macos_arm64}` during
`generate-test-resources`, then `chmod 755` the macOS ones. Bumping the solver is a matter of
changing the three `solvers-langevin-*.version` properties in `pom.xml` (currently `1.4.2`,
kept in lockstep) — the profiles are otherwise near-duplicates, so edit all three.

`localsolvers/`, `installers/`, and `install4j_output/` are gitignored build outputs, but the
four `localsolvers/*/.gitkeep` files are tracked so the directory skeleton survives a fresh
clone; `SpringSaLaDAll.install4j` explicitly excludes them from the bundle.

`pom.xml` declares three jogamp repositories (`jogamp-remote`, `jogamp-Java3d`,
`jogamp-test`). The extras were added to work around a Java3D artifact signature problem —
don't prune them as duplicates.

### Tests

`mvn test` runs a JUnit 5 suite under `src/test/java`. Surefire forces `java.awt.headless=true`;
the code under test is Swing-adjacent, so keep tests off any class that opens a window.

The suite covers the two contracts with the external solver, not the GUI:

- `GlobalRoundTripTest` — loads the tracked `example_files/example.txt`, writes it back, reloads,
  and asserts the structure and the exact `*** SECTION ***` literals survive. Copy the fixture to
  a temp dir first: `Global.writeFile()` overwrites the file it was loaded from.
- `SiteTypeTest` — grammar of the `TYPE:` line, including a regression test for commit `4f05dc5`.
- `IOHelpTest` — `DF[n]` precision, and the locale hazard noted below.
- `ViewerTrajectoryFormatTest` — characterization of the solver's `_VIEW_Run0.txt` trajectory
  and `SiteIDs.csv`.
- `SpringSaladTrajectoryTest`, `SpringSaladViewerCanvasTest`, `SpringSaladMovieExporterTest`,
  `TrajectoryFilesTest`, `Vect3dTest`, `ViewerIsJava2dOnlyTest` — the Java2D viewer. The canvas
  and exporter tests render offscreen via `renderToImage`, so they run headless in CI.

**Test fixtures must be self-contained.** `example_files/example_SIMULATIONS/` is gitignored, so
simulation output — trajectories, `SiteIDs.csv`, CSVs — exists only in a working copy that has
run the GUI. A fresh clone and CI have `example_files/example.txt` and nothing else. Trimmed
fixtures live in `src/test/resources/`.

GUI behaviour is still verified by hand: run against `example_files/example.txt`.

### CI / release

- `.github/workflows/ant.yml` — builds on every push (Maven only; nothing is published).
- `.github/workflows/deploy-installers.yml` — **the live release path.** Triggered by pushing
  any tag (or manual dispatch with a tag). Installs install4j 10.0.5, runs the Maven build,
  decodes the Apple/Windows signing keystores from repo secrets, calls
  `generate_client_installers.sh`, and publishes everything in `installers/` to a GitHub
  release for that tag.
- `.github/workflows/zenodo-archive.yml` — on release publish, archives to Zenodo via the
  reusable workflow in `virtualcell/zenodo-maint`. `zenodo-drift.yml` re-checks weekly.
  Record metadata lives in `.zenodo.json` and `CITATION.cff` (concept DOI
  `10.5281/zenodo.11270119`); keep the author lists in those two files in sync.
- `.github/workflows/release.yml` — **stale.** It uploads `./SpringSalad_<tag>.jar`, which
  no part of the build produces. Superseded by `deploy-installers.yml`; don't extend it.

`generate_client_installers.sh` drives install4j. It `source`s a gitignored `install4j.env`
for local use, and otherwise takes `CONFIG_DIR MAVEN_ROOT_DIR INSTALL4J_PATH [LICENSE]` as
positional arguments (how CI calls it), with keystore passwords and the version `TAG` coming
from the environment.

`SpringSaLaDAll.install4j` is fully parameterized by install4j compiler variables
(`mavenRootDir`, `macKeystore`, `windowsKeystore`, `springSaladVersion`), all injected via
`-D` by that script — the checked-in defaults are deliberate placeholders like
`NO-MAVEN-DIR`. It bundles `target/springsalad-0.0.1-SNAPSHOT.jar`, `target/dependency` (as
`dependent_jars`), the icon resources, and `localsolvers`, with a JBR 17 JRE.

macOS notarization is implemented in `.github/mac_notarize.sh` but the job that calls it is
commented out in `deploy-installers.yml`, so released DMGs are signed but **not** notarized.

The Maven artifact version is permanently `0.0.1-SNAPSHOT`. The *product* version (currently
2.4.2) comes from the git tag at release time via `springSaladVersion`, so install4j no longer
needs editing — but two copies are still hand-maintained and drift easily: the About text in
`MainGUI.java` (~line 951) and the version line in `README.md`. (`AboutBox.java` has its own
stale hardcoded version and appears unused.)

## Architecture

Three sequential user-facing stages, each with its own top-level frame:

1. **Model setup** — `langevinsetup.MainGUI`, a `JTree` (`SystemTree`) on the left driving a
   swap-in editor panel on the right, all mutating one `Global` instance.
2. **Simulation launch** — `runlauncher.LauncherFrame`, opened from `MainGUI` once the model
   has been saved to a file.
3. **Analysis & visualization** — `clusteranalysis.DataGUI2` and
   `viewer.SpringSaladViewerFrame`, both opened from `LauncherFrame` for a finished simulation.

### The `Global` model and its file format

`langevinsetup.Global` is the single in-memory model root: molecules, binding/transition/
allosteric/decay reactions, `BoxGeometry`, `SystemTimes`, counters, and annotations. Nearly
every GUI class takes a `Global` and mutates it directly — there is no separate
controller/persistence layer.

Serialization is hand-rolled `PrintWriter` writing and `Scanner` parsing
(`Global.writeFile()` / `loadFile()`, plus static `loadX(...)` methods on the domain classes).
The format is a plain-text file with `*** SECTION NAME ***` headers whose exact strings are
the `public final static String` constants at the top of `Global` (`MOLECULES`,
`BIMOLECULAR BINDING REACTIONS`, …). **Changing any of those strings or the line grammar
breaks both saved user models and the external solver's parser**, which reads the same file.
See `example_files/example.txt` for a complete instance.

`runlauncher.Simulation extends Global`: a simulation is a *copy* of the model file plus run
state (run count, parallel flag, progress, output-file locations), appended to the same file
under extra `SIMULATION STATE` / `PROCESSOR FILES` / … sections.

### On-disk layout produced by a run

For a model saved as `example.txt`, everything is derived by string manipulation on that name:

```
example.txt
example_SIMULATIONS/                       # created by SimulationManager
  Simulation0_SIM.txt                      # a Simulation; "_SIM.txt" suffix is how they're discovered
  Simulation0_OutStream_0.txt              # per-run progress log, polled by ProgressPanel, deleted on success
  Simulation0_SIM_FOLDER/
    data/                                  # CSVs: raw per-run, averages, RunningTimes.csv, Cluster_stat/
    viewer_files/                          # *_VIEW_Run<N>.txt trajectories for the 3D viewer
    images/ videos/
```

These conventions are hardcoded in `SimulationManager`, `Simulation`, `DataProcessor`
(`dataFolder = drive + "/" + simulationName + "_FOLDER/data/"`), and
`clusteranalysis.PathCreator`. Change one and you must change the others.

### Running a simulation

`Simulation.runSimulationWithProcessBuilder()` creates one progress file and `ProgressPanel`
per run, then hands off to `runlauncher.RunLauncher`, which on a background thread spawns

```
<solver> simulate --output-log <outStream_i.txt> <Simulation0_SIM.txt> <runIndex>
```

once per run — sequentially, or in fixed-size waves of `numberSimultaneousRuns`. Progress is
communicated *through the log files*: `ProgressPanel` tails them, and when all panels report
finished, `Simulation.stateChanged()` fires post-processing (`calculateStatistics()`), writes
the sim file, and deletes the OutStream files.

`SimulationManager.warmUp()` and `ClassLauncher` are vestigial — they reflect an older design
where the solver was an in-process Java class (`langevinnovis01.Global`) rather than a
subprocess. The live path is `ProcessBuilder`.

### Post-processing: two generations

There are two parallel analysis stacks, and the newer one subclasses the older:

- `dataprocessor.DataProcessor` / `DataGUI` — original: per-time-point averages and standard
  deviations across runs for molecule, bond, state, and site counters; histograms; heat maps.
- `clusteranalysis.DataProcessor2 extends DataProcessor`, `DataGUI2 extends DataGUI` — adds
  cluster analysis (`ClusterAnalyzer` → `ClusterStatsProducer`), driven by `PathCreator`
  conventions and `CSVHandler`/`MyCSVReader`.

`LauncherFrame` always instantiates the `2` variants. `PostProcessingDocument.txt` at the repo
root is a short call-graph sketch of this flow.

### Package map

| Package | Role |
|---|---|
| `langevinsetup` | Domain model (`Global`, `Molecule`, `Site`, `State`, reactions, counters) **and** its Swing editors, intermixed. Largest package. |
| `runlauncher` | `Simulation`, `SimulationManager`, `RunLauncher`, progress UI, per-simulation parameter-override editors |
| `dataprocessor` | Gen-1 statistics + tables/histograms/heat maps |
| `clusteranalysis` | Gen-2 statistics, cluster stats, CSV I/O |
| `viewer` | Java2D trajectory viewer (`SpringSaladViewerCanvas`, `SpringSaladTrajectory`), GIF/MP4 export via jcodec |
| `render` | Software 3D math for the viewer — `Camera`, `Trackball`, `Affine`, `Vect3d`, `Quaternion`, `Matrix3d` |
| `jmolintegration` | Jmol viewer for PDB-derived molecule structures |
| `helpersetup` | Swing helpers — `PopUp`, `Fonts`, `Colors`, `Constraints`, `IOHelp` |
| `helpernovis` | Numerics shared with the solver's conventions (`OnRateSolver`, `Rand`, `Location`) |

## The trajectory viewer

`viewer.SpringSaladViewerFrame` renders the solver's `_VIEW_Run<N>.txt` trajectory with
**Java2D only**. The renderer (`SpringSaladViewerCanvas`, `SpringSaladTrajectory`) and the 3D
math in `render/` were ported from VCell (`virtualcell/vcell`), which is also MIT and reads the
same solver file with an independently written parser. Ported files carry a provenance note;
**if you fix a bug in one of them, send it back to VCell** or the two copies drift.

`Colors`/`NamedColor` were deliberately *not* ported — VCell's are a verbatim copy of this
project's `helpersetup` versions, so the canvas imports ours.

Two things to know before changing it:

- **`SiteIDs.csv` is not beside the trajectory.** The trajectory is in
  `<sim>_FOLDER/viewer_files/`; the site identities are in `<sim>_FOLDER/data/Run<N>/`.
  `TrajectoryFiles` handles both. Without that file the viewer can only group sites by colour and
  radius, and types that share both collapse into one visibility toggle — which is exactly what
  happens on the shipped example, where every site is RED at radius 1.0.
- **The example trajectory is a poor test subject for rendering.** Its paired sites sit ~0.2
  apart at radius 1.0, so the canvas correctly suppresses their bonds as overlapping and hiding
  one site type leaves an identical-looking site on the same pixels. Renderer tests use a
  synthetic trajectory instead; both behaviours are pinned by their own tests.

### Java3D is still a dependency

Replacing the trajectory viewer did **not** remove Java3D, and the jogamp repos and
`--add-exports java.desktop/sun.awt=ALL-UNNAMED` all have to stay. Two other places still use it:

- `langevinsetup` — `DrawPanel3D`, `DrawPanel3DPanel`, `SiteSphere`, `LinkCylinder` (~1,310
  lines): the molecule editor's *interactive* 3D preview, with picking and selection sync to
  `MoleculeEditor`. This is a separate application from the trajectory viewer and VCell has no
  equivalent to port — replacing it means writing one.
- `jmolintegration` — imports `org.jogamp.vecmath.Matrix3f`.

`ViewerIsJava2dOnlyTest` fails if anything in `viewer/` or `render/` imports jogamp again.

## Conventions in this codebase

- Swing tables follow a consistent `XTableModel` + `XTablePanel` pair per concept; adding a new
  editable model property usually means touching the domain class, its `TableModel`, the
  `write`/`load` methods in `Global`, and the corresponding `runlauncher.EditX*` override panel.
- NetBeans `.form` files exist next to `MainGUI.java`, `MoleculeEditor.java`, and
  `SiteArrayCreator.java`. The generated regions in those `.java` files are marked with
  `<editor-fold ... "Generated Code">` — hand-editing them will desync the designer.
- Exceptions are typically swallowed with `e.printStackTrace(System.out)`; `PopUp` is used for
  user-facing errors. Match the surrounding style rather than introducing logging.
- Numbers written into the model file are formatted through `IOHelp.DF[n]`, an array of
  `DecimalFormat`s indexed by decimal places (0–8). The index is a **lossy truncation of the
  user's input as seen by the solver**, not just display formatting — commit 4f05dc5 fixed
  diffusion coefficients silently rounding to 3 decimals in `SiteType.writeType()`. Be
  deliberate when picking an index for a new field.
- **Known latent bug:** those `DecimalFormat`s are built in a static initializer without a
  `Locale`, so they bind the JVM default. On a comma-decimal locale (de, fr, es…) the app writes
  `D 1,50000` into the model file and the solver's `Double.parseDouble` cannot read it. Unfixed —
  `IOHelpTest` documents the mechanism and guards the current locale.
