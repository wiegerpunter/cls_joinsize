package org.experiments;
import org.jfree.data.xy.*;

import java.util.HashMap;
import java.util.Map;

public class DatasetBuilder {

    public static XYDataset buildDataset(
            Map<ResultAggregator.Key, ResultAggregator.Agg> data,
            String metric // "error", "absError", "ingestTime", "queryTime"
    ) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        Map<String, XYSeries> seriesMap = new HashMap<>();

        for (var e : data.entrySet()) {
            var key = e.getKey();
            var agg = e.getValue();

            String seriesName = key.setting + " | latent=" + key.numLatentAttrs;
            seriesMap.putIfAbsent(seriesName, new XYSeries(seriesName));

            double y = switch (metric) {
                case "error" -> agg.errorSum / agg.count;
                case "absError" -> agg.absErrorSum / agg.count;
                case "ingestTime" -> agg.ingestSum / (double) agg.count;
                case "queryTime" -> agg.querySum / (double) agg.count;
                default -> throw new IllegalArgumentException();
            };

            seriesMap.get(seriesName).add(key.ram, y);
        }

        seriesMap.values().forEach(dataset::addSeries);
        return dataset;
    }
}
