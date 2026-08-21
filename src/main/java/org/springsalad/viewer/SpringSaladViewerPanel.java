package org.springsalad.viewer;

import org.springsalad.helpersetup.PopUp;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The trajectory viewer: a {@link SpringSaladViewerCanvas} with transport controls, a frame
 * slider, scene toggles, a site-type legend and movie export.
 *
 * <p>Behaviour follows VCell's SpringSaladViewerPanel, but none of its code: that class is wired
 * into VCell's {@code AsynchClientTask} / {@code PopupGenerator} / {@code VCFileChooser}
 * machinery. This one uses this project's conventions -- {@link PopUp} for user-facing errors, a
 * plain {@link JFileChooser}, and a {@link SwingWorker} for the export.
 */
public class SpringSaladViewerPanel extends JPanel {

    private static final int[] SPEEDS = {2, 5, 10, 20, 30};

    private final SpringSaladViewerCanvas canvas = new SpringSaladViewerCanvas();
    private final SiteTypeLegend legend = new SiteTypeLegend(canvas);
    private final JSlider frameSlider = new JSlider(0, 0, 0);
    private final JButton playButton = new JButton("Play");
    private final JButton saveMovieButton = new JButton("Save movie…");
    private final JLabel readout = new JLabel(" ");
    private final JComboBox<String> speedCombo = new JComboBox<>(speedLabels());
    private final Timer timer;

    /** Set while an export is running so the user cannot change the view under it. */
    private boolean exporting;

    public SpringSaladViewerPanel() {
        super(new BorderLayout());
        timer = new Timer(100, e -> advanceFrame());
        timer.setRepeats(true);

        JPanel sceneToggles = new JPanel();
        sceneToggles.setLayout(new BoxLayout(sceneToggles, BoxLayout.Y_AXIS));
        sceneToggles.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        sceneToggles.add(sceneToggle("Links", true, canvas::setShowLinks));
        sceneToggles.add(sceneToggle("Box", true, canvas::setShowBox));
        sceneToggles.add(sceneToggle("Membrane", true, canvas::setShowMembrane));

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.add(sceneToggles, BorderLayout.NORTH);
        sidebar.add(legend, BorderLayout.CENTER);
        sidebar.setPreferredSize(new Dimension(190, 400));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, sidebar);
        split.setResizeWeight(1.0);
        split.setDividerLocation(620);
        add(split, BorderLayout.CENTER);
        add(buildControls(), BorderLayout.SOUTH);

        speedCombo.setSelectedIndex(2); // 10 fps
        speedCombo.addActionListener(e -> applySpeed());
        applySpeed();

        playButton.addActionListener(e -> togglePlay());
        saveMovieButton.addActionListener(e -> saveMovie());
        frameSlider.addChangeListener(e -> {
            canvas.setFrameIndex(frameSlider.getValue());
            updateReadout();
        });
        setTrajectory(null);
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new BorderLayout(6, 0));
        controls.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        controls.add(playButton, BorderLayout.WEST);
        controls.add(frameSlider, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        right.add(new JLabel("Speed:"));
        right.add(speedCombo);
        JButton reset = new JButton("Reset view");
        reset.addActionListener(e -> canvas.resetView());
        right.add(reset);
        right.add(saveMovieButton);
        right.add(Box.createHorizontalStrut(8));
        right.add(readout);
        controls.add(right, BorderLayout.EAST);
        return controls;
    }

    /** Show a trajectory, or {@code null} to clear the viewer. */
    public void setTrajectory(SpringSaladTrajectory trajectory) {
        stop();
        canvas.setTrajectory(trajectory);
        int frames = canvas.getFrameCount();
        frameSlider.setMinimum(0);
        frameSlider.setMaximum(Math.max(0, frames - 1));
        frameSlider.setValue(0);
        frameSlider.setEnabled(frames > 1);
        playButton.setEnabled(frames > 1);
        saveMovieButton.setEnabled(frames > 0);
        legend.rebuild();
        updateReadout();
    }

    public SpringSaladViewerCanvas getCanvas() {
        return canvas;
    }

    private JCheckBox sceneToggle(String label, boolean initial, Consumer<Boolean> apply) {
        JCheckBox box = new JCheckBox(label, initial);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.addActionListener(e -> apply.accept(box.isSelected()));
        return box;
    }

    // ---- transport ----

    private void togglePlay() {
        if (timer.isRunning()) {
            stop();
        } else {
            start();
        }
    }

    private void start() {
        if (canvas.getFrameCount() > 1) {
            timer.start();
            playButton.setText("Pause");
        }
    }

    private void stop() {
        timer.stop();
        playButton.setText("Play");
    }

    private void advanceFrame() {
        int frames = canvas.getFrameCount();
        if (frames == 0) {
            stop();
            return;
        }
        frameSlider.setValue((canvas.getFrameIndex() + 1) % frames); // wraps; listener repaints
    }

    private void applySpeed() {
        timer.setDelay(Math.max(1, 1000 / selectedFps()));
    }

    private int selectedFps() {
        int i = speedCombo.getSelectedIndex();
        return SPEEDS[i < 0 ? 2 : i];
    }

    private static String[] speedLabels() {
        String[] labels = new String[SPEEDS.length];
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = SPEEDS[i] + " fps";
        }
        return labels;
    }

    private void updateReadout() {
        SpringSaladTrajectory t = canvas.getTrajectory();
        if (t == null || t.getFrameCount() == 0) {
            readout.setText("No trajectory");
            return;
        }
        SpringSaladTrajectory.Frame f = t.getFrames().get(canvas.getFrameIndex());
        readout.setText(String.format(Locale.ROOT, "frame %d/%d   t = %.4g s",
                canvas.getFrameIndex() + 1, t.getFrameCount(), f.getTime()));
    }

    // ---- movie export ----

    private void saveMovie() {
        if (exporting || canvas.getFrameCount() == 0) {
            return;
        }
        stop();

        SpringSaladMovieExporter.Format format = askFormat();
        if (format == null) {
            return;
        }
        File file = askFile(format);
        if (file == null) {
            return;
        }

        int width = Math.max(320, canvas.getWidth());
        int height = Math.max(240, canvas.getHeight());
        int fps = selectedFps();

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        JOptionPane pane = new JOptionPane(new Object[]{"Rendering " + canvas.getFrameCount()
                + " frames…", bar}, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"Cancel"}, "Cancel");
        javax.swing.JDialog dialog = pane.createDialog(this, "Save movie");

        // Named, not anonymous: the Progress callback below has to qualify isCancelled(),
        // and an unqualified call there would bind to the callback's own method and recurse.
        class ExportWorker extends SwingWorker<Boolean, Integer> {
            @Override
            protected Boolean doInBackground() throws Exception {
                return SpringSaladMovieExporter.writeMovie(canvas, file, format, width, height, fps,
                        new SpringSaladMovieExporter.Progress() {
                            @Override
                            public void frameDone(int frame, int total) {
                                publish(100 * (frame + 1) / total);
                            }

                            @Override
                            public boolean isCancelled() {
                                // Qualified: an unqualified isCancelled() here binds to this
                                // anonymous class's own method and recurses until the stack blows.
                                return ExportWorker.this.isCancelled();
                            }
                        });
            }

            @Override
            protected void process(List<Integer> chunks) {
                bar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                dialog.dispose();
                exporting = false;
                setViewLocked(false);
                if (isCancelled()) {
                    return;
                }
                try {
                    if (Boolean.TRUE.equals(get())) {
                        PopUp.information("Saved " + file.getName());
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    PopUp.error("Could not save the movie:\n" + e.getMessage());
                }
            }
        }
        ExportWorker worker = new ExportWorker();

        exporting = true;
        setViewLocked(true);
        worker.execute();
        dialog.setVisible(true);       // modal: blocks until done() disposes it or Cancel is hit
        if (!worker.isDone()) {
            worker.cancel(true);
        }
    }

    /** Keep the user from rotating or re-framing mid-export; the exporter reads the view state. */
    private void setViewLocked(boolean locked) {
        canvas.setEnabled(!locked);
        frameSlider.setEnabled(!locked && canvas.getFrameCount() > 1);
        playButton.setEnabled(!locked && canvas.getFrameCount() > 1);
        saveMovieButton.setEnabled(!locked);
    }

    private SpringSaladMovieExporter.Format askFormat() {
        SpringSaladMovieExporter.Format[] formats = SpringSaladMovieExporter.Format.values();
        Object choice = JOptionPane.showInputDialog(this, "Movie format:", "Save movie",
                JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]);
        return (SpringSaladMovieExporter.Format) choice;
    }

    private File askFile(SpringSaladMovieExporter.Format format) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save movie");
        chooser.setFileFilter(new FileNameExtensionFilter(format.getDescription(),
                format.getExtension().substring(1)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(format.getExtension())) {
            file = new File(file.getParentFile(), file.getName() + format.getExtension());
        }
        if (file.exists() && PopUp.doubleCheck("Overwrite " + file.getName() + "?") != JOptionPane.YES_OPTION) {
            return null;
        }
        return file;
    }
}
