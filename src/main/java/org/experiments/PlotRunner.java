package org.experiments;
public class PlotRunner {

    public static void main(String[] args) throws Exception {
        String csv = args[0];

        var data = ResultAggregator.aggregate(csv);

        PlotUtil.plot(
                DatasetBuilder.buildDataset(data, "absError"),
                "Abs Error vs RAM",
                "Average Abs Error"
        );

        PlotUtil.plot(
                DatasetBuilder.buildDataset(data, "error"),
                "Error vs RAM",
                "Average Error"
        );

        PlotUtil.plot(
                DatasetBuilder.buildDataset(data, "ingestTime"),
                "Ingest Time vs RAM",
                "Avg Ingest Time (ms)"
        );

        PlotUtil.plot(
                DatasetBuilder.buildDataset(data, "queryTime"),
                "Query Time vs RAM",
                "Avg Query Time (ms)"
        );

        PlotUtil.plot(
                DatasetBuilder.buildDataset(data, "relError"),
                "Relative Error vs RAM",
                "Relative Error"
        );
    }
}
