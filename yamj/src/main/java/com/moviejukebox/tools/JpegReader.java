/*
 *      Copyright (c) 2004-2012 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 *      This software is licensed under a Creative Commons License
 *      See this page: http://code.google.com/p/moviejukebox/wiki/License
 *
 *      For any reuse or distribution, you must make clear to others the
 *      license terms of this work.
 */
package com.moviejukebox.tools;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.log4j.Logger;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.byteSources.ByteSource;
import org.apache.sanselan.common.byteSources.ByteSourceFile;
import org.apache.sanselan.formats.jpeg.JpegImageParser;
import org.apache.sanselan.formats.jpeg.segments.UnknownSegment;

/**
 * Custom reader to detect the various forms of the JPEG file.
 *
 * Taken from
 * http://stackoverflow.com/questions/3123574/how-to-convert-from-cmyk-to-rgb-in-java-correctly/12132630#12132630
 *
 * @author stuart.boston
 */
public class JpegReader {

    private static final Logger logger = Logger.getLogger(JpegReader.class);
    private static final String logMessage = "JpegReader: ";
    public static final int COLOR_TYPE_RGB = 1;
    public static final int COLOR_TYPE_CMYK = 2;
    public static final int COLOR_TYPE_YCCK = 3;
    private int colorType = COLOR_TYPE_RGB;
    private boolean hasAdobeMarker = Boolean.FALSE;

    /**
     * Used to read a JPEG image to a BufferedImage and able to cope with a CYMK
     * colour space image
     *
     * @param file
     * @return
     * @throws IOException
     * @throws ImageReadException
     */
    public BufferedImage readImage(File file) throws IOException, ImageReadException {
        colorType = COLOR_TYPE_RGB;
        hasAdobeMarker = Boolean.FALSE;

        if (!file.exists()) {
            logger.warn(logMessage + "File does not exist: " + file.getName());
            return null;
        }

        ImageInputStream stream = ImageIO.createImageInputStream(file);
        Iterator<ImageReader> iter = ImageIO.getImageReaders(stream);
        while (iter.hasNext()) {
            ImageReader reader = iter.next();
            reader.setInput(stream);

            BufferedImage image;
            ICC_Profile profile;
            try {
                image = reader.read(0);
            } catch (IIOException ex) {
                colorType = COLOR_TYPE_CMYK;
                checkAdobeMarker(file);
                profile = Sanselan.getICCProfile(file);
                WritableRaster raster = (WritableRaster) reader.readRaster(0, null);
                if (colorType == COLOR_TYPE_YCCK) {
                    convertYcckToCmyk(raster);
                }
                if (hasAdobeMarker) {
                    convertInvertedColors(raster);
                }
                image = convertCmykToRgb(raster, profile);
            }

            return image;
        }

        return null;
    }

    private void checkAdobeMarker(File file) throws IOException, ImageReadException {
        JpegImageParser parser = new JpegImageParser();
        ByteSource byteSource = new ByteSourceFile(file);
        @SuppressWarnings("rawtypes")
        ArrayList segments = parser.readSegments(byteSource, new int[]{0xffee}, true);
        if (segments != null && segments.size() >= 1) {
            UnknownSegment app14Segment = (UnknownSegment) segments.get(0);
            byte[] data = app14Segment.bytes;
            if (data.length >= 12 && data[0] == 'A' && data[1] == 'd' && data[2] == 'o' && data[3] == 'b' && data[4] == 'e') {
                hasAdobeMarker = Boolean.TRUE;
                int transform = app14Segment.bytes[11] & 0xff;
                if (transform == 2) {
                    colorType = COLOR_TYPE_YCCK;
                }
            }
        }
    }

    private static void convertYcckToCmyk(WritableRaster raster) {
        int height = raster.getHeight();
        int width = raster.getWidth();
        int stride = width * 4;
        int[] pixelRow = new int[stride];
        for (int h = 0; h < height; h++) {
            raster.getPixels(0, h, width, 1, pixelRow);

            for (int x = 0; x < stride; x += 4) {
                int y = pixelRow[x];
                int cb = pixelRow[x + 1];
                int cr = pixelRow[x + 2];

                int c = (int) (y + 1.402 * cr - 178.956);
                int m = (int) (y - 0.34414 * cb - 0.71414 * cr + 135.95984);
                y = (int) (y + 1.772 * cb - 226.316);

                if (c < 0) {
                    c = 0;
                } else if (c > 255) {
                    c = 255;
                }
                if (m < 0) {
                    m = 0;
                } else if (m > 255) {
                    m = 255;
                }
                if (y < 0) {
                    y = 0;
                } else if (y > 255) {
                    y = 255;
                }

                pixelRow[x] = 255 - c;
                pixelRow[x + 1] = 255 - m;
                pixelRow[x + 2] = 255 - y;
            }

            raster.setPixels(0, h, width, 1, pixelRow);
        }
    }

    private static void convertInvertedColors(WritableRaster raster) {
        int height = raster.getHeight();
        int width = raster.getWidth();
        int stride = width * 4;
        int[] pixelRow = new int[stride];
        for (int h = 0; h < height; h++) {
            raster.getPixels(0, h, width, 1, pixelRow);
            for (int x = 0; x < stride; x++) {
                pixelRow[x] = 255 - pixelRow[x];
            }
            raster.setPixels(0, h, width, 1, pixelRow);
        }
    }

    private static BufferedImage convertCmykToRgb(Raster cmykRaster, ICC_Profile cmykProfile) throws IOException {
        if (cmykProfile == null) {
            cmykProfile = ICC_Profile.getInstance(JpegReader.class.getResourceAsStream("/properties/Generic CMYK Profile.icc"));
        }
        ICC_ColorSpace cmykCS = new ICC_ColorSpace(cmykProfile);
        BufferedImage rgbImage = new BufferedImage(cmykRaster.getWidth(), cmykRaster.getHeight(), BufferedImage.TYPE_INT_RGB);
        WritableRaster rgbRaster = rgbImage.getRaster();
        ColorSpace rgbCS = rgbImage.getColorModel().getColorSpace();
        ColorConvertOp cmykToRgb = new ColorConvertOp(cmykCS, rgbCS, null);
        cmykToRgb.filter(cmykRaster, rgbRaster);
        return rgbImage;
    }
}
