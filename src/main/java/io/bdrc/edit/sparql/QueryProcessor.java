package io.bdrc.edit.sparql;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;

import io.bdrc.edit.EditConfig;

public class QueryProcessor {

    public static Model describeModel(String fullUri) {
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + " describe <" + fullUri.trim() + ">");
        final QueryExecution qe = QueryExecutionFactory.sparqlService(EditConfig.getProperty(EditConfig.FUSEKI_URL), q);
        return qe.execDescribe();
    }

    public static void dropGraph(String graphUri) {
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        fusConn.delete(graphUri);
    }

    public static void main(String[] args) {
        EditConfig.init();
        // Model m =
        // QueryProcessor.describeModel("http://purl.bdrc.io/admindata/P1583");
        // m.write(System.out, "TURTLE");
        dropGraph("http://purl.bdrc.io/graph/P1524X");
    }

}
