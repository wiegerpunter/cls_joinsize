package org.synopses.ChowMin;

public class AttributeDimensionOneRep {
    int attribute;
    int codomainSize;
    int valuesPerCodomain; //[rows][1 .. w]

    AttributeDimensionOneRep(int attribute, int codomain) {
        this.attribute = attribute;
        codomainSize = 1;
        valuesPerCodomain = codomain;
    }
}
