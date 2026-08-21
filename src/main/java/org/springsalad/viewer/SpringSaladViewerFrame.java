package org.springsalad.viewer;

import org.springsalad.helpersetup.PopUp;
import org.springsalad.runlauncher.Simulation;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Window around {@link SpringSaladViewerPanel}: picks which run to show and loads it off the EDT.
 *
 * <p>Replaces the Java3D {@link ViewerGUI} for viewing trajectories. Opened from the launcher's
 * "View Trajectory" button.
 */
public class SpringSaladViewerFrame extends JFrame {

    private final SpringSaladViewerPanel viewer = new SpringSaladViewerPanel();
    private final JComboBox<TrajectoryFiles.Run> runCombo = new JComboBox<>();

    private SpringSaladViewerFrame(String title, List<TrajectoryFiles.Run> runs) {
        super(title + " - Trajectory");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        for (TrajectoryFiles.Run run : runs) {
            runCombo.addItem(run);
        }
        runCombo.addActionListener(e -> loadSelected());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(new JLabel("Run:"));
        top.add(runCombo);
        if (runs.size() == 1) {
            runCombo.setEnabled(false); // nothing to choose between
        }

        add(top, BorderLayout.NORTH);
        add(viewer, BorderLayout.CENTER);
        setSize(1000, 700);
        setLocationRelativeTo(null);
    }

    /**
     * Open the viewer for a simulation, or explain why it cannot be opened. Safe to call from any
     * thread; the window is built on the EDT and the trajectory is parsed off it.
     */
    public static void open(String title, Simulation simulation) {
        List<TrajectoryFiles.Run> runs = TrajectoryFiles.runsFor(simulation);
        if (runs.isEmpty()) {
            PopUp.warning("This simulation has no trajectory files.\n"
                    + "Trajectories are written for Run 0 only, and only when the simulation has run.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            SpringSaladViewerFrame frame = new SpringSaladViewerFrame(title, runs);
            frame.setVisible(true);
            frame.loadSelected();
        });
    }

    /** Parse the selected run off the EDT -- a long trajectory takes a moment. */
    private void loadSelected() {
        TrajectoryFiles.Run run = (TrajectoryFiles.Run) runCombo.getSelectedItem();
        if (run == null) {
            return;
        }
        viewer.setTrajectory(null);
        new SwingWorker<SpringSaladTrajectory, Void>() {
            @Override
            protected SpringSaladTrajectory doInBackground() throws Exception {
                return run.load();
            }

            @Override
            protected void done() {
                try {
                    viewer.setTrajectory(get());
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    PopUp.error("Could not read the trajectory:\n" + e.getMessage());
                }
            }
        }.execute();
    }
}
