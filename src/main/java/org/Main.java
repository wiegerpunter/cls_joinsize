package org;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import java.util.*;
import java.util.stream.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataset.DataGenerator;
import org.experiments.RunExperiment;
import org.synopses.*;
import org.utils.Config;
import org.utils.Edge;

import static org.ChowLiu.chowLiu;
import static org.ChowLiu.computeExactMutualInformation;

import org.utils.Record;


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    public static void main(String[] args) throws Exception {
        String jsonFilePath = args[0];
        ObjectMapper mapper = new ObjectMapper();
        Config config = mapper.readValue(new File(jsonFilePath), Config.class);

        DataGenerator generator = new DataGenerator(config.N, config.cardinalities);
        generator.run(config);
        int[][] data = generator.getDataset();
        int[][] queries = generator.getQueries();
        int[] exactAnswer = generator.getExactAnswer();
        List<Edge> chowLiuTree = computeExactMutualInformation(data);

        RunExperiment re = new RunExperiment(config, data, queries, exactAnswer, chowLiuTree);


    }


}