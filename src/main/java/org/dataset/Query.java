package org.dataset;

import net.sf.jsqlparser.JSQLParserException;

public class Query {
    String query_name;
    final String query_text;
    int query_result;
    public SynopsisQuery syn_query;
    Query(String query_name, String query_text) throws JSQLParserException {
        this.query_name = query_name;
        this.query_text = query_text;
        buildSynQuery();
    }

    public void buildSynQuery() throws JSQLParserException {
        // IMDB-JOB sql dialect
        // convert sql query to query that can be executed on a synopsis
        this.syn_query = new SynopsisQuery(query_text);
    }

    public int getResult() {
        return query_result;
    }

    public String getName() {
        return query_name;
    }
}
