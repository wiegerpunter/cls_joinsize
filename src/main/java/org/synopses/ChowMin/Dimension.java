package org.synopses.ChowMin;

public class Dimension {
    final int attribute;
    final int row;
    final int[] values;

    Dimension(int attribute, int row, int[] values) {
        this.attribute = attribute;
        this.row = row;
        this.values = values;
    }
}
