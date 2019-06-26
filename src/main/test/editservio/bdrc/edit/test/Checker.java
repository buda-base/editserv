package editservio.bdrc.edit.test;

import java.io.IOException;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.sparql.Prefixes;

public class Checker {

    // Checks the resource ID is now the object of a given ?s ?p
    public static boolean checkResourceInConstruct(String checkFile, String resId) throws IOException {
        String q = PostTaskTest.getResourceFileContent(checkFile);
        final Query qy = QueryFactory.create(Prefixes.getPrefixesString() + " " + q);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(EditConfig.getProperty(EditConfig.FUSEKI_URL), qy);
        Model m = qe.execConstruct();
        NodeIterator ni = m.listObjects();
        while (ni.hasNext()) {
            RDFNode node = ni.next();
            if (node.asResource().getLocalName().equals(resId)) {
                return true;
            }
        }
        return false;
    }

}
