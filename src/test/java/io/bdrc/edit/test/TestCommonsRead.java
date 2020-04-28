package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.CommonsRead;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

public class TestCommonsRead {

    static {
        EditConfig.init();
    }

    @Test
    public void getGraphTest() {
        String graphUri = "http://purl.bdrc.io/resource/P1583";
        String graphUri1 = "http://purl.bdrc.io/graph/P1583";
        String graphUri2 = "http://purl.bdrc.io/resource/P1583XZ";

        try {
            CommonsRead.getGraph(graphUri1);
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            // TODO Auto-generated catch block
            assert (e instanceof UnknownBdrcResourceException);
        }

        try {
            CommonsRead.getGraph(graphUri2);
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            // TODO Auto-generated catch block
            assert (e instanceof NotModifiableException);
        }

        try {
            Model m = CommonsRead.getGraph(graphUri);
            assert (m.size() > 0);
            m.write(System.out, "TURTLE");
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void getFocusGraphTest() throws IOException {
        String modelFile = "P707.ttl";
        String fullShapes = "fullPersonShapes.ttl";
        Model person = ModelFactory.createDefaultModel();
        InputStream in = TestCommonsRead.class.getClassLoader().getResourceAsStream(modelFile);
        person.read(in, null, "TTL");
        List<String> l = CommonsRead.getFocusPropertiesFromShape(fullShapes, CommonsRead.GRAPH_RESOUCE);
        Model res = CommonsRead.getFocusGraph("bdr:P707", person, fullShapes, CommonsRead.GRAPH_RESOUCE);
        assert (res.size() > 0);
        assert (res.size() <= person.size());
        StmtIterator it = res.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.next();
            assert (l.contains(stmt.getPredicate().getURI()));
        }
    }

}
