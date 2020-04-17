package io.bdrc.edit.test;

import java.io.IOException;

import org.apache.jena.rdf.model.Model;
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

}
