package org.utils;

public class Edge {
    int s, t;
    public Edge(int s, int t) {
        this.s = s;
        this.t = t;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true; // same object
        if (!(obj instanceof Edge other)) return false;
        if (this.s == other.s && this.t == other.t) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return s + t;
    }

    public int getS() {
        return s;
    }

    public int getT() {
        return t;
    }

}