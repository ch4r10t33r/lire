/*
 * This file is part of the LIRE project: http://www.semanticmetadata.net/lire
 * LIRE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * LIRE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LIRE; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * We kindly ask you to refer the any or one of the following publications in
 * any publication mentioning or employing Lire:
 *
 * Lux Mathias, Savvas A. Chatzichristofis. Lire: Lucene Image Retrieval –
 * An Extensible Java CBIR Library. In proceedings of the 16th ACM International
 * Conference on Multimedia, pp. 1085-1088, Vancouver, Canada, 2008
 * URL: http://doi.acm.org/10.1145/1459359.1459577
 *
 * Lux Mathias. Content Based Image Retrieval with LIRE. In proceedings of the
 * 19th ACM International Conference on Multimedia, pp. 735-738, Scottsdale,
 * Arizona, USA, 2011
 * URL: http://dl.acm.org/citation.cfm?id=2072432
 *
 * Mathias Lux, Oge Marques. Visual Information Retrieval using Java and LIRE
 * Morgan & Claypool, 2013
 * URL: http://www.morganclaypool.com/doi/abs/10.2200/S00468ED1V01Y201301ICR025
 *
 * Copyright statement:
 * --------------------
 * (c) 2002-2013 by Mathias Lux (mathias@juggle.at)
 *     http://www.semanticmetadata.net/lire, http://www.lire-project.net
 */

package net.semanticmetadata.lire.imageanalysis;

import net.semanticmetadata.lire.utils.ImageUtils;
import net.semanticmetadata.lire.utils.MetricsUtils;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * The PHOG descriptor is described in Anna Bosch, Andrew Zisserman & Xavier Munoz (2007) "Representing shape with a
 * spatial pyramid kernel", CVIR 2007. It basically combines histograms of edges in several spatial pyramid levels.
 *
 * @author Mathias Lux, mathias@juggle.at, 05.04.13
 */
public class PHOG implements LireFeature {
    static ConvolveOp sobelX = new ConvolveOp(new Kernel(3, 3, new float[]{1, 0, -1, 2, 0, -2, 1, 0, -1}));
    static ConvolveOp sobelY = new ConvolveOp(new Kernel(3, 3, new float[]{1, 2, 1, 0, 0, 0, -1, -2, -1}));
    static ConvolveOp gaussian = new ConvolveOp(new Kernel(5, 5, ImageUtils.makeGaussianKernel(5, 1.4f)));
    static ColorConvertOp grayscale = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
    int[] tmp255 = {255};
    int[] tmp128 = {128};
    int[] tmp000 = {0};
    int[] tmpPixel = {0};
    // double thresholds for Canny edge detector
    double thresholdLow = 60, thresholdHigh = 100;

    // And now for PHOG:
    public static int bins = 40;
    double[] histogram;


    public void extract(BufferedImage bimg) {
        // All for Canny Edge ...
        BufferedImage gray;
        double[][] gx, gy;
        double[][] gd, gm;

        // doing canny edge detection first:
        // filter images:
        gray = grayscale.filter(bimg, new BufferedImage(bimg.getWidth(), bimg.getHeight(), BufferedImage.TYPE_BYTE_GRAY));
//        gray = gaussian.filter(gray, null);
        gx = sobelFilterX(gray);
        gy = sobelFilterY(gray);
        int width = gray.getWidth();
        int height = gray.getHeight();
        gd = new double[width][height];
        gm = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // setting gradient magnitude and gradient direction
                if (gx[x][y] != 0) {
                    gd[x][y] = Math.atan(gy[x][y] / gx[x][y]);
                } else {
                    gd[x][y] = Math.PI / 2d;
                }
                gm[x][y] = Math.hypot(gy[x][y], gx[x][y]);
            }
        }
        // Non-maximum suppression
        for (int x = 0; x < width; x++) {
            gray.getRaster().setPixel(x, 0, new int[]{255});
            gray.getRaster().setPixel(x, height - 1, new int[]{255});
        }
        for (int y = 0; y < height; y++) {
            gray.getRaster().setPixel(0, y, new int[]{255});
            gray.getRaster().setPixel(width - 1, y, new int[]{255});
        }
        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                if (gd[x][y] < (Math.PI / 8d) && gd[x][y] >= (-Math.PI / 8d)) {
                    if (gm[x][y] > gm[x + 1][y] && gm[x][y] > gm[x - 1][y])
                        setPixel(x, y, gray, gm[x][y]);
                    else
                        gray.getRaster().setPixel(x, y, tmp255);
                } else if (gd[x][y] < (3d * Math.PI / 8d) && gd[x][y] >= (Math.PI / 8d)) {
                    if (gm[x][y] > gm[x - 1][y - 1] && gm[x][y] > gm[x - 1][y - 1])
                        setPixel(x, y, gray, gm[x][y]);
                    else
                        gray.getRaster().setPixel(x, y, tmp255);
                } else if (gd[x][y] < (-3d * Math.PI / 8d) || gd[x][y] >= (3d * Math.PI / 8d)) {
                    if (gm[x][y] > gm[x][y + 1] && gm[x][y] > gm[x][y + 1])
                        setPixel(x, y, gray, gm[x][y]);
                    else
                        gray.getRaster().setPixel(x, y, tmp255);
                } else if (gd[x][y] < (-Math.PI / 8d) && gd[x][y] >= (-3d * Math.PI / 8d)) {
                    if (gm[x][y] > gm[x + 1][y - 1] && gm[x][y] > gm[x - 1][y + 1])
                        setPixel(x, y, gray, gm[x][y]);
                    else
                        gray.getRaster().setPixel(x, y, tmp255);
                } else {
                    gray.getRaster().setPixel(x, y, tmp255);
                }
            }
        }
        // hysteresis ... walk along lines of strong pixels and make the weak ones strong.
        int[] tmp = {0};
        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                if (gray.getRaster().getPixel(x, y, tmp)[0] < 50) {
                    // It's a strong pixel, lets find the neighbouring weak ones.
                    trackWeakOnes(x, y, gray);
                }
            }
        }
        // removing the single weak pixels.
        for (int x = 2; x < width - 2; x++) {
            for (int y = 2; y < height - 2; y++) {
                if (gray.getRaster().getPixel(x, y, tmp)[0] > 50) {
                    gray.getRaster().setPixel(x, y, tmp255);
                }
            }
        }

        // TODO: more bins, more levels, more histogram quantization.
        // Canny Edge Detection over ... lets go for the PHOG ...
        histogram = new double[5 * bins + 4*4*bins + 4*4*4*4*bins];
        // for level 3:
//        histogram = new double[5 * bins + 4*4*bins + 4*4*4*4*bins];
        //level0
        System.arraycopy(getHistogram(0, 0, width, height, gray, gd), 0, histogram, 0, bins);
        //level1
        System.arraycopy(getHistogram(0, 0, width / 2, height / 2, gray, gd),
                0, histogram, bins, bins);
        System.arraycopy(getHistogram(width / 2, 0, width / 2, height / 2, gray, gd),
                0, histogram, 2 * bins, bins);
        System.arraycopy(getHistogram(0, height / 2, width / 2, height / 2, gray, gd),
                0, histogram, 3 * bins, bins);
        System.arraycopy(getHistogram(width / 2, height / 2, width / 2, height / 2, gray, gd),
                0, histogram, 4 * bins, bins);
        // level 2
        int wstep = width / 4;
        int hstep = height / 4;
        int binPos = 5; // the next free section in the histogram
        for (int i = 0; i< 4; i++) {
            for (int j=0; j<4; j++) {
                System.arraycopy(getHistogram(i*wstep, j*hstep, wstep, hstep, gray, gd),
                        0, histogram, binPos*bins, bins);
                binPos++;
            }
        }
        // level 3
//        wstep = width / 16;
//        hstep = height / 16;
//        for (int i = 0; i< 16; i++) {
//            for (int j=0; j<16; j++) {
//                System.arraycopy(getHistogram(i*wstep, j*hstep, wstep, hstep, gray, gd),
//                        0, histogram, binPos*bins, bins);
//                binPos++;
//            }
//        }
    }

    /**
     * Create and normalize histogram.
     *
     * @param startX
     * @param startY
     * @param width
     * @param height
     * @param gray
     * @param gd
     * @return
     */
    private double[] getHistogram(int startX, int startY, int width, int height, BufferedImage gray, double gd[][]) {
        int[] tmp = {0};
        double[] result = new double[bins];
        double actual = 0;
        int bin;
        // set initial histogram to 0
        for (int i = 0; i < result.length; i++) result[i] = 0;
        // find and increment the right bin/s
        for (int x = startX; x < startX + width; x++) {
            for (int y = startY; y < startY + height; y++) {
                if (gray.getRaster().getPixel(x, y, tmp)[0] < 50) {
                    // it's an edge pixel, so it counts in.
                    actual = (gd[x][y] / Math.PI + 0.5) * (bins);
                    if (actual == Math.floor(actual)) {  // if it's a discrete thing ...
                        bin = ((int) Math.floor(actual));
                        if (bin == bins) bin = 0;
                        result[bin] += 1;
                    } else { // in between: we make it fuzzy ...
                        bin = ((int) Math.floor(actual));
                        if (bin == bins) bin = 0;
                        result[bin] += actual - Math.floor(actual);
                        bin = (int) Math.ceil(actual);
                        if (bin == bins) bin = 0;
                        result[bin] += Math.ceil(actual) - actual;
                    }
                }
            }
        }
        // normalize histogram to max norm.
        double max = 0d;
        for (int i = 0; i < result.length; i++) {
            max = Math.max(result[i], max);
        }
        if (max > 0d) {
            for (int i = 0; i < result.length; i++) {
                // quantize single values to 32 steps to compress feature a little bit.
                result[i] = Math.round(31d * result[i] / max);
            }
        }
        return result;
    }

    /**
     * Recursive tracking of weak points.
     *
     * @param x
     * @param y
     * @param gray
     */
    private void trackWeakOnes(int x, int y, BufferedImage gray) {
        for (int xx = x - 1; xx <= x + 1; xx++)
            for (int yy = y - 1; yy <= y + 1; yy++) {
                if (isWeak(xx, yy, gray)) {
                    gray.getRaster().setPixel(xx, yy, tmp000);
                    trackWeakOnes(xx, yy, gray);
                }
            }
    }

    private boolean isWeak(int x, int y, BufferedImage gray) {
        return (gray.getRaster().getPixel(x, y, tmpPixel)[0] > 0 && gray.getRaster().getPixel(x, y, tmpPixel)[0] < 255);
    }

    private void setPixel(int x, int y, BufferedImage gray, double v) {
        if (v > thresholdHigh) gray.getRaster().setPixel(x, y, tmp000);
        else if (v > thresholdLow) gray.getRaster().setPixel(x, y, tmp128);
        else gray.getRaster().setPixel(x, y, tmp255);
    }

    private double[][] sobelFilterX(BufferedImage gray) {
        double[][] result = new double[gray.getWidth()][gray.getHeight()];
        int[] tmp = new int[4];
        int tmpSum = 0;
        for (int x = 1; x < gray.getWidth() - 1; x++) {
            for (int y = 1; y < gray.getHeight() - 1; y++) {
                tmpSum = 0;
                tmpSum += gray.getRaster().getPixel(x - 1, y - 1, tmp)[0];
                tmpSum += 2 * gray.getRaster().getPixel(x - 1, y, tmp)[0];
                tmpSum += gray.getRaster().getPixel(x - 1, y + 1, tmp)[0];
                tmpSum -= gray.getRaster().getPixel(x + 1, y - 1, tmp)[0];
                tmpSum -= 2 * gray.getRaster().getPixel(x + 1, y, tmp)[0];
                tmpSum -= gray.getRaster().getPixel(x + 1, y + 1, tmp)[0];
                result[x][y] = tmpSum;
            }
        }
        for (int x = 0; x < gray.getWidth(); x++) {
            result[x][0] = 0;
            result[x][gray.getHeight() - 1] = 0;
        }
        for (int y = 0; y < gray.getHeight(); y++) {
            result[0][y] = 0;
            result[gray.getWidth() - 1][y] = 0;
        }
        return result;
    }

    private double[][] sobelFilterY(BufferedImage gray) {
        double[][] result = new double[gray.getWidth()][gray.getHeight()];
        int[] tmp = new int[4];
        int tmpSum = 0;
        for (int x = 1; x < gray.getWidth() - 1; x++) {
            for (int y = 1; y < gray.getHeight() - 1; y++) {
                tmpSum = 0;
                tmpSum += gray.getRaster().getPixel(x - 1, y - 1, tmp)[0];
                tmpSum += 2 * gray.getRaster().getPixel(x, y - 1, tmp)[0];
                tmpSum += gray.getRaster().getPixel(x + 1, y - 1, tmp)[0];
                tmpSum -= gray.getRaster().getPixel(x - 1, y + 1, tmp)[0];
                tmpSum -= 2 * gray.getRaster().getPixel(x, y + 1, tmp)[0];
                tmpSum -= gray.getRaster().getPixel(x + 1, y + 1, tmp)[0];
                result[x][y] = tmpSum;
            }
        }
        for (int x = 0; x < gray.getWidth(); x++) {
            result[x][0] = 0;
            result[x][gray.getHeight() - 1] = 0;
        }
        for (int y = 0; y < gray.getHeight(); y++) {
            result[0][y] = 0;
            result[gray.getWidth() - 1][y] = 0;
        }
        return result;
    }

    public byte[] getByteArrayRepresentation() {
        byte[] result = new byte[histogram.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) histogram[i];
//            System.out.println("result[i]-histogram[i] = " + (result[i] - histogram[i]));
        }
        return result;
    }

    public void setByteArrayRepresentation(byte[] in) {
        histogram = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            histogram[i] = (double) in[i];
        }
    }

    public void setByteArrayRepresentation(byte[] in, int offset, int length) {
        histogram = new double[length];
        for (int i = offset; i < length; i++) {
            histogram[i] = (double) in[i];
        }
    }

    public double[] getDoubleHistogram() {
        return histogram;
    }

    public float getDistance(LireFeature feature) {
        // chi^2 distance ... as mentioned in the paper.
//        double distance = 0;
//        double lower;
//        for (int i = 0; i < histogram.length; i++) {
//            lower = histogram[i] + ((PHOG) feature).histogram[i];
//            if (lower > 0)
//                distance += (histogram[i] - ((PHOG) feature).histogram[i]) * (histogram[i] - ((PHOG) feature).histogram[i]) / lower;
//        }
//        return (float) distance;
        return (float) MetricsUtils.distL1(histogram, ((PHOG) feature).histogram);
    }

    @Override
    public String getStringRepresentation() {
        return null;
    }

    @Override
    public void setStringRepresentation(String s) {

    }
}