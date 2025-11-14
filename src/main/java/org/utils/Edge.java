package org.utils;

public class Edge {
    int u, v;
    public Edge(int u, int v) {
        this.u = u;
        this.v = v;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true; // same object
        if (!(obj instanceof Edge other)) return false;
        if (this.u == other.u && this.v == other.v) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return u + v;
    }

    public int getU() {
        return u;
    }

    public int getV() {
        return v;
    }

}