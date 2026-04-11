package com.agent;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

public class WebPEncoder {

    private static final float DEFAULT_QUALITY = 0.75f; // 75% качество (хороший баланс)

    public static byte[] encode(BufferedImage image) {
        return encode(image, DEFAULT_QUALITY);
    }

    public static byte[] encode(BufferedImage image, float quality) {
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
            if (!writers.hasNext()) {
                System.err.println("WebP writer not found, falling back to JPEG");
                return encodeAsJPEG(image);
            }

            ImageWriter writer = writers.next();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            ios.close();

            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("WebP encoding failed: " + e.getMessage());
            return encodeAsJPEG(image);
        }
    }

    private static byte[] encodeAsJPEG(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}