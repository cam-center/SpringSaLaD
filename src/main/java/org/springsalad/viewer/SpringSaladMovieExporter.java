package org.springsalad.viewer;

import org.jcodec.api.awt.AWTSequenceEncoder;

import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Writes a {@link SpringSaladViewerCanvas}'s trajectory out as a movie, in the view the user has
 * set up -- same rotation, zoom, site-type selection and box/membrane/link toggles.
 *
 * <p>MP4 is encoded with jcodec, a pure-Java H.264 encoder, so no native codec has to be
 * installed. Animated GIF uses this project's existing {@link GifSequenceWriter} and is capped at
 * 256 colours, which shows as banding on the shaded spheres; MP4 is the better choice for anything
 * but a quick embed.
 *
 * <p>Modelled on VCell's SpringSaladMovieExporter (also MIT), but written against this project's
 * own GIF writer and without VCell's task/progress framework. Frames are rendered and encoded one
 * at a time rather than collected into an array first, as {@link MovieMaker} does: a 101-frame
 * 640x480 export would otherwise hold ~124 MB of images in memory at once.
 */
public class SpringSaladMovieExporter {

    /** Output formats offered by the export dialog. */
    public enum Format {
        MP4(".mp4", "MP4 video (*.mp4)"),
        ANIMATED_GIF(".gif", "Animated GIF (*.gif)");

        private final String extension;
        private final String description;

        Format(String extension, String description) {
            this.extension = extension;
            this.description = description;
        }

        public String getExtension() { return extension; }
        public String getDescription() { return description; }

        @Override
        public String toString() { return description; }
    }

    /** Reports progress and offers cancellation; the panel backs this with its SwingWorker. */
    public interface Progress {
        /** @param frame zero-based frame just finished, {@code total} frames in all */
        void frameDone(int frame, int total);

        /** @return true to abandon the export */
        boolean isCancelled();
    }

    /** H.264 codes in 16x16 macroblocks; frame sizes are rounded down to a whole number of them. */
    public static final int MACROBLOCK = 16;

    private SpringSaladMovieExporter() {
    }

    /**
     * Render every frame of the canvas's trajectory and write it to {@code file}.
     *
     * <p>Call this off the EDT -- encoding hundreds of frames takes seconds to minutes. It does not
     * mutate the canvas, but it does read its view state, so the caller must keep the user from
     * changing the view while it runs.
     *
     * @return true if the movie was written, false if {@code progress} cancelled it (in which case
     *         the partial file is deleted)
     */
    public static boolean writeMovie(SpringSaladViewerCanvas canvas, File file, Format format,
                                     int width, int height, int fps, Progress progress)
            throws IOException {
        int frameCount = canvas.getFrameCount();
        if (frameCount == 0) {
            throw new IOException("no trajectory frames to export");
        }
        // Encode at a whole number of macroblocks. H.264 can crop a partial one, but then the frame
        // the decoder holds is larger than the frame the container advertises, and players that
        // ignore the crop show padding at the edges. Rounding down sidesteps it for at most 15px.
        int w = Math.max(MACROBLOCK, width - (width % MACROBLOCK));
        int h = Math.max(MACROBLOCK, height - (height % MACROBLOCK));

        return format == Format.MP4
                ? writeMp4(canvas, file, w, h, fps, frameCount, progress)
                : writeAnimatedGif(canvas, file, w, h, fps, frameCount, progress);
    }

    private static boolean writeMp4(SpringSaladViewerCanvas canvas, File file, int w, int h, int fps,
                                    int frameCount, Progress progress) throws IOException {
        AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(file, fps);
        boolean complete = false;
        try {
            for (int i = 0; i < frameCount; i++) {
                if (cancelled(progress)) {
                    return false;
                }
                encoder.encodeImage(canvas.renderFrameToImage(i, w, h));
                report(progress, i, frameCount);
            }
            encoder.finish();
            complete = true;
            return true;
        } finally {
            if (!complete) {
                // finish() writes the MP4 index; without it the file is unplayable, so drop it
                deleteQuietly(file);
            }
        }
    }

    private static boolean writeAnimatedGif(SpringSaladViewerCanvas canvas, File file, int w, int h,
                                            int fps, int frameCount, Progress progress) throws IOException {
        // Milliseconds BETWEEN frames, not frames per second. MovieMaker.makeAnimagedGIF passes
        // fps*1000 here, which makes a 10 fps export play one frame every ten seconds.
        int delayMs = Math.max(10, Math.round(1000f / fps));
        boolean complete = false;
        try (ImageOutputStream out = new FileImageOutputStream(file)) {
            GifSequenceWriter writer =
                    new GifSequenceWriter(out, BufferedImage.TYPE_INT_RGB, delayMs, true);
            for (int i = 0; i < frameCount; i++) {
                if (cancelled(progress)) {
                    return false;
                }
                writer.writeToSequence(canvas.renderFrameToImage(i, w, h));
                report(progress, i, frameCount);
            }
            writer.close();
            complete = true;
            return true;
        } finally {
            if (!complete) {
                deleteQuietly(file);
            }
        }
    }

    private static boolean cancelled(Progress progress) {
        return progress != null && progress.isCancelled();
    }

    private static void report(Progress progress, int frame, int frameCount) {
        if (progress != null) {
            progress.frameDone(frame, frameCount);
        }
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
