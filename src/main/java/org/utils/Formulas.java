package org.utils;

public class Formulas {

    static double ramOmniKmin(int numAttrsToUse, int depth, int width, int B, int smallb) {
        //return (double) depth * width * numAttrsToUse * (B * (smallb + Math.log(B) + 3 * 32 + 1) + 32);
        return (double) depth * width * numAttrsToUse * (B * smallb + 32);

    }

    public static long ramSingleKmin(int B, int smallb) {
        //return (long) (B * (smallb + Math.log(B) + 3 * 32 + 1) + 32);
        return (long) B * smallb + 32;
    }

    public static int ramASH(int maxSize, int numAttrs) {
        // size * (size of record + size of Double array + Tis array)
        return maxSize * (32 * numAttrs + 5 * 64 + 2 *32); // 5 * 64L for the size of Double array and Tis array, 2*32L for HashMap pointers.
    }

    static int smallb(int B, double delta, int depth) {
        return 31;////(Math.ceil(Math.log(4*Math.pow(B, (double) 5/2)/delta)));
    }

    static int ramOmniTwoLHS(int numAttrsToUse, int depth, int width, int numTwoLHSReps) {
        return depth * width * numAttrsToUse * (numTwoLHSReps * 31 * 32 * 32 + 32);
        //return depth * width * numAttrsToUse * (numTwoLHSReps * 31 * 32 + 32);
    }

    static int ramOmniRef(int numAttrsToUse, int depth, int B, int width) {
        return (int) (depth * numAttrsToUse * B  * (Math.log(B)/Math.log(2) + Math.log(width)/Math.log(2)));
    }

    static int ramOmniArr(int numAttrsToUse, int depth, int B) {
        return (int) ((depth * numAttrsToUse + 1) * B  * 32L);
    }

    public static long ramASHWithBins(int maxSize, int numAttrs, int numJoinKeys) {
        return maxSize * (32L * (numAttrs + numJoinKeys) + 5L * 64L + 2L *32L);
    }
}
