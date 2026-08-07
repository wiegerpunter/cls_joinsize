package org.experiments;
import java.io.*;
import java.util.*;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.jfree.data.xy.*;

public class ResultAggregator {

    static class Key {
        String setting;
        int numLatentAttrs;
        int ram;

        Key(String s, int n, int r) {
            setting = s;
            numLatentAttrs = n;
            ram = r;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return setting.equals(k.setting)
                    && numLatentAttrs == k.numLatentAttrs
                    && ram == k.ram;
        }

        @Override public int hashCode() {
            return Objects.hash(setting, numLatentAttrs, ram);
        }
    }

    static class Agg {
        double errorSum, absErrorSum, relErrorSum;
        int ingestSum, querySum;
        int count;
    }

    public static Map<Key, Agg> aggregate(String csvFile) throws IOException, CsvValidationException {
        Map<Key, Agg> map = new HashMap<>();

        CSVReader reader = new CSVReader(new FileReader(csvFile));
        String[] f;
        reader.readNext();
        while ((f =reader.readNext()) != null) {
            String setting = f[1];
            int numLatentAttrs = Integer.parseInt(f[3]);
            int ingestTime = Integer.parseInt(f[4]);
            int queryTime = Integer.parseInt(f[5]);
            double error = Double.parseDouble(f[8]);
            double absError = Double.parseDouble(f[9]);
            double relError = Double.parseDouble(f[10]);
            int ram = Integer.parseInt(f[13]);
            int memUsage = Integer.parseInt(f[2]);

            Key key = new Key(setting, numLatentAttrs, memUsage);
            map.computeIfAbsent(key, k -> new Agg());

            Agg a = map.get(key);
            a.errorSum += error;
            a.absErrorSum += absError;
            a.relErrorSum += relError;
            a.ingestSum += ingestTime;
            a.querySum += queryTime;
            a.count++;
        }
        reader.close();
        return map;
    }
}
