package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.Reasoner;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.BDRCReasoner;
import io.bdrc.libraries.Prefixes;

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
    static Triple T1;

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
        // OntologyData.init();
        missingObjects = new ArrayList<>(Arrays.asList(P1585, P8528, P2JM192, P2JM193, P2JM194));
        T1 = new Triple(NodeFactory.createURI("http://purl.bdrc.io/resource/P705"),
                NodeFactory.createURI("http://purl.bdrc.io/ontology/core/hasFather"), NodeFactory.createURI("http://purl.bdrc.io/resource/P2MS9526"));
    }

    // @Test
    public void checkRemovedTriples() throws IOException {
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

    // @Test
    public void checkSymetricAndInverse() {
        assert (OntologyData.isSymmetric(kinWith.getURI()));
        Resource r = OntologyData.getInverse(personTeacherOf.getURI());
        assert (r.getURI().equals("http://purl.bdrc.io/ontology/core/personStudentOf"));
    }

    public void displayInverseSymetric() {
        Model initial = ModelFactory.createDefaultModel();
        InputStream in = TestModelUtils.class.getClassLoader().getResourceAsStream("P705.ttl");
        initial.read(in, null, "TTL");
        // initial.write(System.out, "TURTLE");
        // System.out.println("TEST :" + initial.listStatements().toSet());
        List<Statement> inverses = CommonsValidate.getNeighboursFromInverse(initial.listStatements().toSet());
        List<Statement> symetrics = CommonsValidate.getNeighboursFromSymmetric(initial.listStatements().toSet());
        System.out.println("Neighbours Inverse >>" + inverses);
        System.out.println("Neighbours Symmetric >>" + symetrics);
    }

    @Test
    public void findInverseTriple() throws IOException, UnknownBdrcResourceException, NotModifiableException {
        String subject = T1.getSubject().getURI();
        Model sub = CommonsGit.getGraphFromGit(EditConstants.BDR + subject.substring(subject.lastIndexOf("/") + 1));
        String object = T1.getObject().getURI();
        Model obj = CommonsGit.getGraphFromGit(EditConstants.BDR + object.substring(object.lastIndexOf("/") + 1));
        SimpleSelector ss1 = new SimpleSelector(ResourceFactory.createResource(T1.getObject().getURI()), (Property) null,
                ResourceFactory.createResource(T1.getSubject().getURI()));
        List<Statement> st1 = obj.listStatements(ss1).toList();
        System.out.println("IN OBJECT Model Before inference >>" + st1);
        Model union = ModelFactory.createDefaultModel();
        union.add(obj);
        union.add(sub);
        // union.write(System.out, "TURTLE");
        Reasoner reasoner = BDRCReasoner.getReasonerWithSymetry();
        InfModel inf = ModelFactory.createInfModel(reasoner, obj);
        // Model inferred = inf.getDeductionsModel();
        inf.setNsPrefixes(Prefixes.getPrefixMapping());
        // inf.write(System.out, "TURTLE");
        System.out.println("After inference List of relevant triples to process further ?");
        List<Statement> st = inf.listStatements(ss1).toList();
        System.out.println(st);
    }

}
