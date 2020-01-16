package io.bdrc.edit.sparql;

import org.apache.jena.atlas.logging.Log;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.libraries.Prefixes;

public class QueryProcessor {

    public final static Logger log = LoggerFactory.getLogger(QueryProcessor.class.getName());

    public static Model describeModel(String fullUri, String fusekiUrl) {
        if (fusekiUrl == null) {
            fusekiUrl = EditConfig.getProperty("fusekiData");
        }
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + " describe <" + fullUri.trim() + ">");
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        return qe.execDescribe();
    }

    public static void dropGraph(String graphUri, String fusekiDataUrl) {
        try {
            if (fusekiDataUrl == null) {
                fusekiDataUrl = EditConfig.getProperty("fusekiData");
            }
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(fusekiDataUrl);
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            fusConn.delete(graphUri);
            fusConn.close();
        } catch (Exception e) {
            Log.error("QueryProcessor dropGraph", e.getMessage());
        }
    }

    public static Model getTriplesWithObject(String fullUri, String fusekiUrl) {
        if (fusekiUrl == null) {
            fusekiUrl = EditConfig.getProperty(EditConfig.FUSEKI_URL);
        }
        String query = "construct {?s ?p <" + fullUri + ">} where { { ?s ?p <" + fullUri + "> } }";
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        return qe.execConstruct();
    }

    public static Model getGraph(String fullUri, String fusekiUrl) {
        if (fusekiUrl == null) {
            fusekiUrl = EditConfig.getProperty(EditConfig.FUSEKI_URL);
        }
        String query = "construct {?s ?p ?o} where { {<" + fullUri + "> { ?s ?p ?o } }}";
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        return qe.execConstruct();
    }

    public static ResultSet getSelectResultSet(String query, String fusekiUrl) {
        if (fusekiUrl == null) {
            fusekiUrl = EditConfig.getProperty(EditConfig.FUSEKI_URL);
        }
        final Query q = QueryFactory.create(Prefixes.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        return qe.execSelect();
    }

    public static boolean resourceExist(String fullUri, String fusekiUrl) {
        Model m = getGraph(fullUri, fusekiUrl);
        if (m != null) {
            return m.size() > 0;
        } else {
            return false;
        }
    }

    public static QueryExecution getResultSet(String query, String fusekiUrl) {
        if (fusekiUrl == null) {
            fusekiUrl = EditConfig.getProperty(EditConfig.FUSEKI_URL);
        }
        QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, QueryFactory.create(query));
        qe.setTimeout(Long.parseLong(EditConfig.getProperty(EditConfig.QUERY_TIMEOUT)));
        return qe;
    }

    public static void main(String[] args) {
        EditConfig.init();
        Model m = QueryProcessor.getTriplesWithObject("http://purl.bdrc.io/resource/P1583", null);
        m.write(System.out, "TURTLE");
        // dropGraph("http://purl.bdrc.io/graph/P1524X");
    }

}
