package io.bdrc.edit.sparql;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
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

    public static Model getTriplesWithObject(String fullUri) {
        String query = "construct {?s ?p <" + fullUri + ">} where { { ?s ?p <" + fullUri + "> } }";
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(EditConfig.getProperty(EditConfig.FUSEKI_URL), q);
        return qe.execConstruct();
    }

    public static Model getGraph(String fullUri) {
        String query = "construct {?s ?p ?o} where { {<" + fullUri + "> { ?s ?p ?o } }}";
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(EditConfig.getProperty(EditConfig.FUSEKI_URL), q);
        return qe.execConstruct();
    }

    public static ResultSet getSelectResultSet(String query) {
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(EditConfig.getProperty(EditConfig.FUSEKI_URL), q);
        return qe.execSelect();
    }

    public static boolean resourceExist(String fullUri) {
        Model m = getGraph(fullUri);
        if (m != null) {
            return m.size() > 0;
        } else {
            return false;
        }
    }

    public static void main(String[] args) {
        EditConfig.init();
        Model m = QueryProcessor.getTriplesWithObject("http://purl.bdrc.io/resource/P1583");
        m.write(System.out, "TURTLE");
        // dropGraph("http://purl.bdrc.io/graph/P1524X");
    }

}
