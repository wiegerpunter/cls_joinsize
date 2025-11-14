package org.utils;

import java.util.Arrays;

public class Record {
    int[] rec;

    public Record(int[] rec) {
        this.rec = rec;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true; // same object
        if (!(obj instanceof Record other)) return false;
        return Arrays.equals(this.rec, other.rec);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rec);
    }

    public int[] getRec() {
        return rec;
    }
}