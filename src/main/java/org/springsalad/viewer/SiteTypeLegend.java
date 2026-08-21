package org.springsalad.viewer;

import org.springsalad.helpersetup.Fonts;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The site-type list beside the canvas: one checkbox per site type, each with a ball icon in that
 * type's colour, toggling {@link SpringSaladViewerCanvas#setSiteTypeVisible}.
 *
 * <p>Types are keyed by {@link SpringSaladTrajectory#siteTypeKey}, so this and the canvas always
 * agree on what a "type" is. When the run supplied {@code SiteIDs.csv} that key names a real
 * molecule and site type; otherwise it falls back to colour and radius, and several distinct types
 * can collapse into one entry -- which is why {@link TrajectoryFiles} goes looking for that file.
 */
public class SiteTypeLegend extends JPanel {

    private final SpringSaladViewerCanvas canvas;
    private final JPanel entries = new JPanel();

    public SiteTypeLegend(SpringSaladViewerCanvas canvas) {
        super(new BorderLayout());
        this.canvas = canvas;
        entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Site types");
        title.setFont(Fonts.SUBTITLEFONT);
        title.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));

        add(title, BorderLayout.NORTH);
        add(new JScrollPane(entries), BorderLayout.CENTER);
    }

    /** Rebuild the list from the canvas's current trajectory. Safe to call repeatedly. */
    public void rebuild() {
        entries.removeAll();
        SpringSaladTrajectory trajectory = canvas.getTrajectory();
        if (trajectory != null) {
            for (Map.Entry<String, Entry> e : collectTypes(trajectory).entrySet()) {
                entries.add(checkBox(e.getKey(), e.getValue()));
            }
        }
        entries.add(Box.createVerticalGlue());
        entries.revalidate();
        entries.repaint();
    }

    /**
     * One entry per distinct site type, in first-seen order, taken from the first frame -- which is
     * every type present at t=0. Types that only appear later (creation reactions) are picked up
     * because we sweep all frames; the cost is one pass over the trajectory, done once per load.
     */
    private static Map<String, Entry> collectTypes(SpringSaladTrajectory trajectory) {
        Map<String, Entry> byKey = new LinkedHashMap<>();
        for (SpringSaladTrajectory.Frame frame : trajectory.getFrames()) {
            for (SpringSaladTrajectory.Site site : frame.getSites()) {
                byKey.computeIfAbsent(trajectory.siteTypeKey(site),
                        k -> new Entry(label(trajectory, site), site.getColor()));
            }
        }
        return byKey;
    }

    private static String label(SpringSaladTrajectory trajectory, SpringSaladTrajectory.Site site) {
        SpringSaladTrajectory.SiteIdentity identity = trajectory.getSiteIdentity(site.getId());
        if (identity != null) {
            return identity.getMoleculeName() + " : " + identity.getSiteTypeName();
        }
        // No SiteIDs.csv for this run: say what we are actually grouping by, rather than inventing
        // a molecule name we do not have.
        return site.getColor() + " r=" + site.getRadius();
    }

    private JCheckBox checkBox(String key, Entry entry) {
        JCheckBox box = new JCheckBox(entry.label, canvas.isSiteTypeVisible(key));
        box.setIcon(SpringSaladViewerCanvas.ballIcon(
                SpringSaladViewerCanvas.colorForName(entry.colorName), 12));
        box.setSelectedIcon(box.getIcon());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.addActionListener(e -> canvas.setSiteTypeVisible(key, box.isSelected()));
        return box;
    }

    private static final class Entry {
        final String label;
        final String colorName;
        Entry(String label, String colorName) {
            this.label = label;
            this.colorName = colorName;
        }
    }
}
