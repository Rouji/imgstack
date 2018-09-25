package at.r0.imgstack;

import org.opencv.core.Mat;
import org.opencv.core.KeyPoint;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.Point;
import org.opencv.core.DMatch;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.calib3d.Calib3d;
import org.opencv.imgproc.Imgproc;
import org.opencv.features2d.ORB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Stacker
{
    private ORB orb;
    private DescriptorMatcher matcher;
    private static final double GOOD_MATCH_PERC = 0.15;
    private static final int MAX_FEATURES = 500;
    private MatOfKeyPoint baseKP;
    private Mat baseDesc;

    public Stacker(Mat baseGrey)
    {
        orb = ORB.create(MAX_FEATURES);
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

        baseKP = new MatOfKeyPoint();
        baseDesc = new Mat();
        orb.detectAndCompute(baseGrey, new Mat(), baseKP, baseDesc);
    }

    public void stack(Mat cover, Mat coverGrey, Mat out)
    {
        MatOfKeyPoint mkp2 = new MatOfKeyPoint();
        Mat desc2 = new Mat();
        MatOfDMatch mdmatch = new MatOfDMatch();
        List<DMatch> lmatch;

        orb.detectAndCompute(coverGrey, new Mat(), mkp2, desc2);

        matcher.match(baseDesc, desc2, mdmatch);
        lmatch = mdmatch.toList();
        lmatch.sort(Comparator.comparing((m) -> m.distance));
        lmatch = lmatch.subList(0, (int)(lmatch.size()*GOOD_MATCH_PERC));

        List<Point> p1 = new ArrayList<>(lmatch.size());
        List<Point> p2 = new ArrayList<>(lmatch.size());
        KeyPoint[] akp1 = baseKP.toArray();
        KeyPoint[] akp2 = mkp2.toArray();
        for (DMatch m : lmatch)
        {
            p1.add(akp1[m.queryIdx].pt);
            p2.add(akp2[m.trainIdx].pt);
        }

        MatOfPoint2f mp1 = new MatOfPoint2f();
        MatOfPoint2f mp2 = new MatOfPoint2f();
        mp1.fromList(p1);
        mp2.fromList(p2);

        Mat homo = Calib3d.findHomography(mp2, mp1, Calib3d.RANSAC);

        Imgproc.warpPerspective(cover, out, homo, cover.size());
    }
}
