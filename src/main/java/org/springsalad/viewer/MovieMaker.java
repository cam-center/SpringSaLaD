/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.springsalad.viewer;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;
import org.monte.media.av.Buffer;
import org.monte.media.av.Format;
import org.monte.media.av.MovieWriter;
import org.monte.media.av.Registry;
import org.monte.media.av.codec.video.VideoFormatKeys;
//import org.monte.media.math.Rational;

import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

//import org.jcodec.api.awt.SequenceEncoder;
import javax.imageio.ImageIO;

import static org.jcodec.api.awt.AWTSequenceEncoder.createSequenceEncoder;

public class MovieMaker {

    public static void makeAVI(File file, BufferedImage [] frames, int fps)
        throws IOException {
        MovieWriter out = Registry.getInstance().getWriter(file);
        
        Format format = new Format(VideoFormatKeys.MediaTypeKey, VideoFormatKeys.MediaType.VIDEO, //
                VideoFormatKeys.EncodingKey, VideoFormatKeys.ENCODING_AVI_PNG,
                VideoFormatKeys.FrameRateKey, new org.monte.media.math.Rational(fps, 1),//
                VideoFormatKeys.WidthKey, frames[0].getWidth(), //
                VideoFormatKeys.HeightKey, frames[0].getHeight(),//
                VideoFormatKeys.DepthKey, 24
                );
        
        int track = out.addTrack(format);
        
        Buffer buf = new Buffer();
        
        buf.format = new Format(VideoFormatKeys.DataClassKey, BufferedImage.class);
        buf.sampleDuration = format.get(VideoFormatKeys.FrameRateKey).inverse();
        for (BufferedImage frame : frames) {
            buf.data = frame;
            out.write(track, buf);
        }
       
        out.close();
        
      
    }
    
    
    public static void makeQuicktime(File file, BufferedImage [] frames, int fps)
        throws IOException {
        SeekableByteChannel out = null;
        try {
            String outputVideoPath = file.getPath();
            out = NIOUtils.writableFileChannel(outputVideoPath);

            int width = frames[0].getWidth();
            int height = frames[0].getHeight();
            AWTSequenceEncoder encoder = new AWTSequenceEncoder(out, Rational.R(fps, 1));
            //SequenceEncoder encoder = createSequenceEncoder(file, fps);
            System.out.println("Frames: " + frames.length);
            int i = 0;
            for (BufferedImage frame : frames) {
                System.out.println("    frane " + i);
                encoder.encodeImage(frame);
                i++;
            }
            encoder.finish();

        } finally {
            NIOUtils.closeQuietly(out);
        }

//        MovieWriter out = Registry.getInstance().getWriter(file);
//
//        Format format = new Format(VideoFormatKeys.MediaTypeKey, VideoFormatKeys.MediaType.VIDEO, //
//                VideoFormatKeys.EncodingKey, VideoFormatKeys.ENCODING_QUICKTIME_PNG,
//                VideoFormatKeys.FrameRateKey, new Rational(fps, 1),//
//                VideoFormatKeys.WidthKey, frames[0].getWidth(), //
//                VideoFormatKeys.HeightKey, frames[0].getHeight(),//
//                VideoFormatKeys.DepthKey, 24
//                );
//
//        int track = out.addTrack(format);
//
//
//        Buffer buf = new Buffer();
//
//        buf.format = new Format(VideoFormatKeys.DataClassKey, BufferedImage.class);
//        buf.sampleDuration = format.get(VideoFormatKeys.FrameRateKey).inverse();
//        for (BufferedImage frame : frames) {
//            buf.data = frame;
//            out.write(track, buf);
//        }
//
//        out.close();
      
    }
    
    public static void makeAnimagedGIF(File file, BufferedImage [] frames, int fps)
        throws IOException {
        
        try (ImageOutputStream output = new FileImageOutputStream(file)) {
            GifSequenceWriter gsw = new GifSequenceWriter(output,frames[0].getType(),fps*1000,false);

            for (BufferedImage bi1 : frames) {
                gsw.writeToSequence(bi1);
            }

            gsw.close();
        } catch(FileNotFoundException fne){
            fne.printStackTrace(System.out);
        } catch(IOException ioe){
            ioe.printStackTrace(System.out);
        }
    }
    
}
