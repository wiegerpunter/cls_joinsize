package org.synopses.ChowMin;

public class AttributeDimension {
    int attribute;
    int codomainSize;
    int[][] valuesPerCodomain; //[rows][1 .. w]

    AttributeDimension(int attribute, int[][] codomain) {
        this.attribute = attribute;
        codomainSize = codomain[0].length;
        valuesPerCodomain = codomain;
    }
}
