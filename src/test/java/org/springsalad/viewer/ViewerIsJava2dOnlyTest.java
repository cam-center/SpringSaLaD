package org.springsalad.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trajectory viewer is Java2D only. Java3D is still a project dependency -- the molecule
 * editor's 3D preview in {@code langevinsetup} needs it -- so nothing stops someone importing
 * jogamp here again by reflex. This fails if they do.
 */
class ViewerIsJava2dOnlyTest {

    private static Path sourceDir(String pkg) {
        Path direct = Paths.get("src/main/java/org/springsalad", pkg);
        return Files.isDirectory(direct) ? direct : Paths.get("..").resolve(direct);
    }

    private static List<String> offendingFiles(String pkg) throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceDir(pkg))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                for (String line : text.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("import ") && t.contains("org.jogamp")) {
                        offenders.add(p.getFileName() + ": " + t);
                    }
                }
            }
        }
        return offenders;
    }

    @Test
    @DisplayName("no class in the viewer package imports Java3D or jogamp")
    void viewerPackageHasNoJava3d() throws IOException {
        assertEquals(List.of(), offendingFiles("viewer"),
                "the Java2D viewer must not depend on Java3D");
    }

    @Test
    @DisplayName("no class in the render package imports Java3D or jogamp")
    void renderPackageHasNoJava3d() throws IOException {
        assertEquals(List.of(), offendingFiles("render"));
    }

    @Test
    @DisplayName("the deleted Java3D viewer classes are really gone")
    void oldViewerClassesRemoved() {
        for (String gone : new String[]{"ViewerGUI", "Scene", "MyCanvas3D", "Axes", "Axis",
                "Membrane", "MovieMaker", "ViewerPanel", "ProgressWindow", "DirectoryMaker"}) {
            assertTrue(Files.notExists(sourceDir("viewer").resolve(gone + ".java")),
                    gone + ".java is back; the Java3D trajectory viewer was removed deliberately");
        }
    }

    @Test
    @DisplayName("GifSequenceWriter is kept -- the new exporter writes GIFs with it")
    void gifWriterRetained() {
        assertTrue(Files.exists(sourceDir("viewer").resolve("GifSequenceWriter.java")));
    }
}
