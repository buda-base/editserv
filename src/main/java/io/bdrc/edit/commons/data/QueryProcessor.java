package io.bdrc.edit.commons.data;

import org.apache.jena.atlas.logging.Log;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;

public class QueryProcessor {

    public final static Logger log = LoggerFactory.getLogger(QueryProcessor.class.getName());

    public static Model describeModel(String fullUri, String fusekiUrl) {
        Model m = ModelFactory.createDefaultModel();
        try {
            final Query q = QueryFactory.create(EditConfig.prefix.getPrefixesString() + " describe <" + fullUri.trim() + ">");
            final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
            m = qe.execDescribe();
        } catch (Exception ex) {
            log.error("could not get describe", ex);
            return m;
        }
        return m;
    }

    public static Model describeModel(String fullUri) {
        return describeModel(fullUri, null);
    }

    public static void dropGraph(String graphUri, String fusekiDataUrl) {
        try {
            RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(fusekiDataUrl);
            RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
            fusConn.delete(graphUri);
            fusConn.close();
        } catch (Exception e) {
            Log.error("QueryProcessor dropGraph", e.getMessage());
        }
    }

    public static Model getTriplesWithObject(String fullUri, String fusekiUrl) {
        String query = "construct {?s ?p <" + fullUri + ">} where { { ?s ?p <" + fullUri + "> } }";
        final Query q = QueryFactory.create(EditConfig.prefix.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        return qe.execConstruct();
    }

    public static Model getGraph(String fullUri) {
        return QueryProcessor.getGraph(fullUri, null);
    }

    public static Model getGraph(String fullUri, String fusekiUrl) {
        String query = " construct {?s ?p ?o} where { graph <" + fullUri + "> { ?s ?p ?o } }";
        log.info("Query Processor looking for graph {} ", fullUri);
        log.info("Query Processor graph query {} ", query);
        final Query q = QueryFactory.create(EditConfig.prefix.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        return qe.execConstruct();
    }

    public static Model getQueryGraph(String fusekiUrl, String query) {
        final Query q = QueryFactory.create(EditConfig.prefix.getPrefixesString() + query);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        return qe.execConstruct();
    }

    public static ResultSet getSelectResultSet(String query, String fusekiUrl) {
        final Query q = QueryFactory.create(EditConfig.prefix.getPrefixesString() + query);
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
        QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, QueryFactory.create(query));
        qe.setTimeout(Long.parseLong(EditConfig.getProperty(EditConfig.QUERY_TIMEOUT)));
        return qe;
    }
    
    

}
