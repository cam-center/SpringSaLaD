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

**Building an installer locally**: use install4j **10.x** — that is what `SpringSaLaDAll.install4j`
and CI target. A machine may have more than one install, so check which you are running before
concluding anything about licences:

```bash
plutil -extract CFBundleShortVersionString raw "/Applications/install4j.app/Contents/Info.plist"
"/Applications/install4j.app/Contents/Resources/app/bin/install4jc" \
  --disable-signing --disable-notarization --faster --disable-bundling -m unixArchive \
  -D mavenRootDir="$PWD",macKeystore=none,windowsKeystore=none,springSaladVersion=0.0.0-test \
  "$PWD/SpringSaLaDAll.install4j"
```

An install4j 11 *evaluation* reports an expired licence, which reads as a licensing problem when
it is really the wrong install: the v11 preferences (`com.install4j.v11.plist`) hold only
`eval=true`, and the organisation's licence is a v10 one.

Build one after changing anything about dependencies or the launcher — `target/dependency` is
bundled wholesale, so a scope mistake ships to users and nothing else catches it.

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

`SpringSaladTrajectory.siteTypeKey` joins the molecule and site type names with a **NUL**, and
that is not stylistic: both names may contain spaces (they are quoted in the model file, which
is what `IOHelp.getNameInQuotes` is for), so a printable separator is ambiguous — `("Actin B",
"Site0")` and `("Actin", "B Site0")` would key the same and share one visibility toggle. It is
written as the escape `'\0'`; typed as a raw control byte it makes the `.java` register as
binary to `grep` and `file(1)`. Matches VCell.

`SpringSaladViewerCanvas.pickSite` is the inverse of what `paintScene` draws, and both take
their projection from the same helpers deliberately. Picking is the only check that catches an
inconsistent screen-to-world mapping: a viewer can rotate perfectly and shade correctly while
clicks land on the wrong object, and no screenshot review sees it. `PickConsistencyTest` closes
the loop — it renders, finds where each site actually landed in pixels, picks there, and
requires the same site back, across rotate/zoom/pan and with one ball squarely behind another.

Two things to know before changing it:

- **`SiteIDs.csv` is not beside the trajectory.** The trajectory is in
  `<sim>_FOLDER/viewer_files/`; the site identities are in `<sim>_FOLDER/data/Run<N>/`.
  `TrajectoryFiles` handles both. Without that file the viewer can only group sites by colour and
  radius, and types that share both collapse into one visibility toggle — which is exactly what
  happens on the shipped example, where every site is RED at radius 1.0.
- **Depth shading is scaled to the scene, not to what is on screen.** Both canvases ramp
  brightness from `viewRadius`/`sceneRadius`, never from the nearest and furthest thing currently
  visible: that makes contrast depend on how many sites happen to be shown, so two sites a hair
  apart come out fully lit and fully dark and swap abruptly as the view turns. The ramp tints the
  sprite's colour — applying it as alpha makes distant sites translucent instead of dark.
- **The example trajectory is a poor test subject for rendering.** Its paired sites sit ~0.2
  apart at radius 1.0, so the canvas correctly suppresses their bonds as overlapping and hiding
  one site type leaves an identical-looking site on the same pixels. Renderer tests use a
  synthetic trajectory instead; both behaviours are pinned by their own tests.

### Handedness is a required argument to the Trackball

`org.springsalad.render` is vendored from VCell's `cbit.vcell.render`, and now matches it:
`new Trackball(camera, Handedness)` takes the caller's depth convention explicitly, with no
default. `projectToSphere_xy` returns `+z` for `RIGHT_HANDED` and `-z` for `LEFT_HANDED`.

This viewer is `RIGHT_HANDED` — `SpringSaladViewerCanvas` projects with +z toward the camera,
shades "nearer = brighter" off the same axis, and converts the mouse to math convention
(`p1y = 1.0 - 2.0 * lastY / h`). VCell's geometry and PDE surface viewers are `LEFT_HANDED` and
feed raw screen y. Ask for the wrong one and the scene turns backwards on **both** axes while
looking perfectly fine in a still image, which is why the constructor makes you decide.

**Handedness is not a free choice upstream, because VCell picks with this math**:
`SurfaceRenderer` builds projected screen polygons from `unProjectPoint(0,0,1)`, `SurfaceCanvas`
hit-tests clicks against them, and `DataValueSurfaceViewer.pickPolygon` hangs off
`mouseClicked`. Get the screen-to-world mapping wrong there and the scene still looks correct
while clicks select the wrong surface — a silent failure this repo would never notice. Nothing
in this viewer picks today, which is what makes right-handed free to adopt here; that stops
being true the moment someone adds "click a site to identify it".

Two traps when working on drag behaviour:

- **A horizontal drag across the midline is identical under either y convention**, so a sign
  error hides there and shows up only on vertical drags. Test both axes.
- `TrackballDirectionTest` calls `rotate_xy` directly, so it pins the *math* convention and not
  the mouse path. A test of what the user actually feels has to go through the canvas's y-flip.

### Java3D is gone; only pure-Java vecmath remains

Nothing renders with Java3D any more. `java3d-core` and `java3d-utils` are removed, and with them
the native artifacts they dragged in (`gluegen-rt`, `jogl-all`, `joal`). Two jogamp repositories
went too — everything now resolves from `central` plus `jogamp-Java3d`, which serves only
`vecmath`. **`--add-exports java.desktop/sun.awt=ALL-UNNAMED` is no longer needed** and is gone
from both `run.sh` and the install4j launcher; it existed for jogl reaching into AWT internals.

`org.jogamp.java3d:vecmath` stays as a direct dependency. It is pure Java with no natives, and
supplies `Matrix3f`, which carries the molecule editor's orientation across to the Jmol view
(`RotationUpdateEvent` → `jmolintegration.Integration`).

Two viewers were rewritten in Java2D:

- `viewer/` — the trajectory viewer, ported from VCell.
- `langevinsetup.DrawPanel3D` — the molecule editor's interactive structure view. Written fresh;
  VCell has no equivalent. Same class name and public API as the Java3D version, so `MainGUI` and
  `DrawPanel3DPanel` were untouched. There is no scene graph now: every paint renders straight
  from the molecule, so `addSite`/`addLink`/`removeLink` just repaint, and the `SiteSphere` and
  `LinkCylinder` wrappers are deleted.

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
- **Numbers on the data path are locale-pinned, deliberately.** Build formatters with
  `IOHelp.decimalFormat(pattern)`, never `new DecimalFormat(pattern)` — the bare constructor binds
  the JVM default locale, and on a comma-decimal one (de, fr, es, pt, ru) it writes `D 1,50000`
  into the model file, which the solver's `Double.parseDouble` cannot read. For the same reason
  the code reads numbers with `Double.parseDouble(sc.next())` rather than `Scanner.nextDouble()`,
  which resolves the decimal separator against the default locale. `CommaLocaleRoundTripTest` and
  `IOHelpTest` guard both halves; the suite passes under en_US, de_DE and fr_FR.
