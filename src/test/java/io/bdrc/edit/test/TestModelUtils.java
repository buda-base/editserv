package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.helpers.ModelUtils;

public class TestModelUtils {

    static Resource P1583 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P1583");
    static Resource P1585 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P1585");
    static Resource P8528 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P8528");
    static Resource P2JM192 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P2JM192");
    static Resource P2JM193 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P2JM193");
    static Resource P2JM194 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P2JM194");
    static Property kinWith = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/kinWith");
    static Property personTeacherOf = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/personTeacherOf");
    static ArrayList<Resource> missingObjects;

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
        OntologyData.init();
        missingObjects = new ArrayList<>(Arrays.asList(P1585, P8528, P2JM192, P2JM193, P2JM194));
    }

    @Test
    public void checkComplement() throws IOException {
        // DIFFERENCES ARE:
        // missing (symetric):
        // bdo:kinWith , bdr:P1585 , bdr:P8528
        // missing (inverseOf):
        // bdo:personTeacherOf bdr:P2JM192 , bdr:P2JM193 , bdr:P2JM194
        Model initial = ModelFactory.createDefaultModel();
        Model edited = ModelFactory.createDefaultModel();
        InputStream in = TestModelUtils.class.getClassLoader().getResourceAsStream("P1583.ttl");
        initial.read(in, null, "TTL");
        in = TestModelUtils.class.getClassLoader().getResourceAsStream("P1583_missingProps.ttl");
        edited.read(in, null, "TTL");
        in.close();
        Set<Statement> removed = ModelUtils.ModelComplementAsSet(initial, edited);
        ArrayList<String> uris = new ArrayList<>();
        for (Statement st : removed) {
            uris.add(st.getObject().asResource().getURI());
        }
        for (Resource r : missingObjects) {
            assert (uris.contains(r.getURI()));
        }
    }

    @Test
    public void checkSymetricAndInverse() {
        assert (OntologyData.isSymmetric(kinWith.getURI()));
        Resource r = OntologyData.getInverse(personTeacherOf.getURI());
        assert (r.getURI().equals("http://purl.bdrc.io/ontology/core/personStudentOf"));
    }

}
