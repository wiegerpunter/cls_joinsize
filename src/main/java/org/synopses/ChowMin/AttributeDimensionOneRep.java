package org.synopses.ChowMin;

public record AttributeDimensionOneRep(int attribute, int valuesPerCodomain) {
    public int getAttribute() {
        return attribute;
    }
    public int getValuesPerCodomain() {
        return valuesPerCodomain;
    }
//    int attribute;
//    int valuesPerCodomain; //[rows][1 .. w]
}
//    AttributeDimensionOneRep {
//        this.attribute = attribute;
//        codomainSize = 1;
//        valuesPerCodomain = codomain;
//    }

