package io.bdrc.edit.commons;

import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.Models;

public class CommonsWrite {

    public static boolean validateCommit(Model newModel, String graphUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        Model current = QueryProcessor.getGraph(graphUri);
        try {
            if (getCommit(newModel, graphUri).equals(getCommit(current, graphUri))) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    public static String getCommit(Model m, String graphUri) {
        String commit = null;
        String shortName = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        SimpleSelector s = new SimpleSelector(ResourceFactory.createResource(Models.BDA + shortName),
                ResourceFactory.createProperty(Models.ADM + "gitRevision"), (RDFNode) null);
        StmtIterator it = m.listStatements(s);
        if (it.hasNext()) {
            Statement st = it.next();
            if (st.getObject().isLiteral()) {
                commit = st.getObject().asLiteral().toString();
            }
        }
        return commit;
    }

}
