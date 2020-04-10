package io.bdrc.edit.test;

import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraph;
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
            DatasetGraph g = CommonsRead.getGraph(graphUri1);
            Model m = ModelFactory.createModelForGraph(g.getUnionGraph());
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            // TODO Auto-generated catch block
            assert (e instanceof UnknownBdrcResourceException);
        }

        try {
            DatasetGraph g = CommonsRead.getGraph(graphUri2);
            Model m = ModelFactory.createModelForGraph(g.getUnionGraph());
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            // TODO Auto-generated catch block
            assert (e instanceof NotModifiableException);
        }

        try {
            DatasetGraph g = CommonsRead.getGraph(graphUri);
            Model m = ModelFactory.createModelForGraph(g.getUnionGraph());
            assert (m.size() > 0);
            m.write(System.out, "TURTLE");
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            e.printStackTrace();
        }
    }

}
