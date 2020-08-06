package io.bdrc.edit.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.Shapes;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsRead;

public class TestShapes {

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
        OntologyData.init();
    }

    // @Test
    public void testEntityShapes() {
        Model shapes_mod = CommonsRead.getValidationShapesForType("bdo:Person");
        shapes_mod.write(System.out, "TURTLE");
        Shapes shapes = Shapes.parse(shapes_mod.getGraph());
    }

    @Test
    public void parseShape() throws IOException {
        Model m = ModelFactory.createDefaultModel();
        InputStream stream = new FileInputStream("/Users/marc/dev/lds-pdi/editor-templates/templates/core/person.local.shapes.ttl");
        m.read("https://raw.githubusercontent.com/buda-base/editor-templates/master/templates/core/event.shapes.ttl", null, "TTL");
        // m.read(stream, null, "TTL");
        Shapes.parse(m.getGraph());
        stream.close();
    }

}
