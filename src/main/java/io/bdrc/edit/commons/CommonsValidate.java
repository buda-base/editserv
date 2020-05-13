package io.bdrc.edit.commons;

import java.io.IOException;

import org.apache.jena.rdf.model.Model;

import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

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

}
