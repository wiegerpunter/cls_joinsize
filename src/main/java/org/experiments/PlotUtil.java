package org.experiments;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.axis.*;
import org.jfree.data.xy.XYDataset;

import javax.swing.*;

public class PlotUtil {

    public static void plot(
            XYDataset dataset,
            String title,
            String yLabel
    ) {
        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                "RAM",
                yLabel,
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        NumberAxis axis = (NumberAxis) chart.getXYPlot().getRangeAxis();
        axis.setAutoRangeIncludesZero(false);

        JFrame frame = new JFrame(title);
        frame.setContentPane(new ChartPanel(chart));
        frame.setSize(900, 600);
        frame.setVisible(true);
    }
}
