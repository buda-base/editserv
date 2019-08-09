package io.bdrc.edit.testClient;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.sparql.QueryProcessor;

public class ResourceProps {

    public static String WORK = "work";
    public static String PLACE = "place";
    public static String PERSON = "person";
    public static String TOPIC = "topic";

    public static HashMap<String, String> map;

    static {
        map = new HashMap<>();
        map.put(WORK, "W22084");
        map.put(PERSON, "P264");
        map.put(PLACE, "G37");
        map.put(TOPIC, "T808");
    }

    public static ArrayList<String> getProps(String resType) {
        String res = map.get(resType);
        String query = "select distinct ?p where { {graph <http://purl.bdrc.io/graph/" + res + "> { bdr:" + res + " ?p ?o } }}";
        ResultSet rs = QueryProcessor.getSelectResultSet(query);
        ArrayList<String> values = new ArrayList<>();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            RDFNode nd = qs.get("?p");
            values.add(nd.asResource().getLocalName());
        }
        return values;
    }

    public static void main(String[] args) {
        EditConfig.init();
        System.out.println(ResourceProps.getProps("topic"));
    }

}
