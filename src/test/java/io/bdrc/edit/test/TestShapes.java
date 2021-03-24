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

}
