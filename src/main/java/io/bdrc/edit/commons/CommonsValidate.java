package io.bdrc.edit.commons;

import java.io.IOException;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.StmtIterator;

import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.Models;

public class CommonsValidate {

    public static boolean validateCommit(Model newModel, String graphUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        Model current = QueryProcessor.getGraph(graphUri);
        try {
            if (CommonsRead.getCommit(newModel, graphUri).equals(CommonsRead.getCommit(current, graphUri))) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    public static boolean existResource(String resourceUri) {
        return (QueryProcessor.describeModel(resourceUri).size() > 0);
    }

    public static boolean isWithdrawn(String resourceUri, boolean prefixed) {
        String shortName = "";
        if (prefixed) {
            shortName = resourceUri.substring(resourceUri.lastIndexOf(":") + 1);
        } else {
            shortName = resourceUri.substring(resourceUri.lastIndexOf("/") + 1);
        }
        String query = "select ?o where { <" + Models.BDA + shortName + "> <" + Models.ADM + "status> ?o }";
        ResultSet rs = QueryProcessor.getSelectResultSet(query, null);
        QuerySolution qs = rs.nextSolution();
        if (qs == null) {
            return true;
        }
        RDFNode n = qs.get("?o");
        if (n.asResource().getURI().equals(Models.BDA + "StatusWithdrawn")) {
            return true;
        }
        return false;
    }

    public static boolean isWithdrawn(Model m, String resourceUri, boolean prefixed) {
        String shortName = "";
        if (prefixed) {
            shortName = resourceUri.substring(resourceUri.lastIndexOf(":") + 1);
        } else {
            shortName = resourceUri.substring(resourceUri.lastIndexOf("/") + 1);
        }
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(Models.BDA + shortName),
                ResourceFactory.createProperty(Models.ADM + "status"), (RDFNode) null);
        StmtIterator it = m.listStatements(ss);
        if (!it.hasNext()) {
            return true;
        }
        RDFNode n = it.next().getObject();
        if (n.asResource().getURI().equals(Models.BDA + "StatusWithdrawn")) {
            return true;
        }
        return false;
    }

}
