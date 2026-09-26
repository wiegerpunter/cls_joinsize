package org;

import java.io.File;
import java.sql.DriverManager;

import java.util.Properties;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataset.DatasetGenerator;
import org.dataset.JoinDataset;
import org.duckdb.DuckDBConnection;
import org.experiments.RunSingleTableExperiment;
import org.experiments.RunJoinExperiment;
import org.utils.Config;
import org.utils.Edge;

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
                    config.numStoredAttributes = config.numAttributes[i];
                    config.setClusters(i);
                    int[][] data = DatasetGenerator.getData(config, config.numAttributes[i]);
                    //        int[][] data = generator.getDataset();
                    DatasetGenerator.Queries Q = DatasetGenerator.genQueries(config.numQueries, config.maxNumPredicates[i], data);
                    int[][] queries = Q.getQueries();
                    int[] exactAnswer = Q.getExactAnswer();
//                    double[][] MIs = computeMIs(data);
//                    List<Edge> chowLiuTree = computeChowLiu(MIs, data[0].length);
                    RunSingleTableExperiment re = new RunSingleTableExperiment(config, data, queries, exactAnswer);
                    re.runAllExperiments(config.seed);
                }
                break;
            case "joinSize":
                Properties props = new Properties();
                props.setProperty("access_mode", "READ_ONLY");
                DuckDBConnection con =
                        (DuckDBConnection) DriverManager.getConnection(
                                "jdbc:duckdb:" + config.duckdbPath, props
                        );
//                DuckDBConnection con =
//                        (DuckDBConnection) DriverManager.getConnection(
//                                "jdbc:duckdb:" + config.duckdbPath
//                        );

                JoinDataset d = new JoinDataset(con, config);

                RunJoinExperiment rje = new RunJoinExperiment(config, d, con);

//                con.close();
                break;
        }



    }


}