package org;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import java.util.*;
import java.util.stream.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataset.DataGenerator;
import org.dataset.DatasetGenerator;
import org.dataset.DuckDBDataset;
import org.dataset.JoinDataset;
import org.duckdb.DuckDBConnection;
import org.experiments.RunExperiment;
import org.synopses.*;
import org.utils.Config;
import org.utils.Edge;

import java.sql.DriverManager;

import org.utils.Record;

import static org.ChowLiu.*;


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    public static void main(String[] args) throws Exception {
        String jsonFilePath = args[0];
        ObjectMapper mapper = new ObjectMapper();
        Config config = mapper.readValue(new File(jsonFilePath), Config.class);

//        DataGenerator generator = new DataGenerator(config.N, config.cardinalities);
//        generator.run(config);
        switch (config.datasetType) {
            case "singleTable":
                for (int i = 0; i < config.numAttributes.length; i++) {
                    config.setClusters(i);
                    int[][] data = DatasetGenerator.getData(config, config.numAttributes[i]);
                    //        int[][] data = generator.getDataset();
                    DatasetGenerator.Queries Q = DatasetGenerator.genQueries(config.numQueries, config.maxNumPredicates[i], data);
                    int[][] queries = Q.getQueries();
                    int[] exactAnswer = Q.getExactAnswer();
                    double[][] MIs = computeMIs(data);
                    List<Edge> chowLiuTree = computeChowLiu(MIs, data[0].length);
                    RunExperiment re = new RunExperiment(config, data, queries, exactAnswer, MIs, chowLiuTree, config.maxNumPredicates[i]);
                }
            case "imdb":
                DuckDBConnection con =
                        (DuckDBConnection) DriverManager.getConnection(
                                "jdbc:duckdb:" + config.duckdbPath
                        );

                JoinDataset d = new JoinDataset(con, config);

                con.close();
        }



    }


}