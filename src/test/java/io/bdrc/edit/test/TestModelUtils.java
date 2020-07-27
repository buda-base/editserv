package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.Reasoner;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

public class TestModelUtils {

    static Resource P1583 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P1583");
    static Resource P1585 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P1585");
    static Resource P8528 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P8528");
    static Resource P2JM192 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P2JM192");
    static Resource P2JM193 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P2JM193");
    static Resource P2JM194 = ResourceFactory.createResource("http://purl.bdrc.io/resource/P2JM194");
    static Property kinWith = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/kinWith");
    static Property hasFather = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/hasFather");
    static Property hasSon = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/hasSon");
    static Property hasBrother = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/hasBrother");
    static Property personTeacherOf = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/personTeacherOf");
    static String BDO = "http://purl.bdrc.io/ontology/core/";
    static String BDR = "http://purl.bdrc.io/resource/";
    static ArrayList<Resource> missingObjects;
    static Triple T1;
    static String owlSchemaBase = "/Users/marc/dev/lds-pdi/owl-schema/";

    static Resource fatherR = ResourceFactory.createResource("http://purl.bdrc.io/resource/PFATHER");
    static Resource sonR = ResourceFactory.createResource("http://purl.bdrc.io/resource/PSON");

    static Model ontmodel = null;
    static Reasoner bdrcReasoner = null;

    @BeforeClass
    public static void init() throws Exception {
        // OntologyData.init();
        missingObjects = new ArrayList<>(Arrays.asList(P1585, P8528, P2JM192, P2JM193, P2JM194));
        T1 = new Triple(NodeFactory.createURI("http://purl.bdrc.io/resource/P705"),
                NodeFactory.createURI("http://purl.bdrc.io/ontology/core/hasFather"), NodeFactory.createURI("http://purl.bdrc.io/resource/P2MS9526"));
        OntDocumentManager ontManager = new OntDocumentManager(owlSchemaBase + "ont-policy.rdf");
        // not really needed since ont-policy sets it, but what if someone changes the
        // policy
        ontManager.setProcessImports(true);
        OntModelSpec ontSpec = new OntModelSpec(OntModelSpec.OWL_DL_MEM);
        ontSpec.setDocumentManager(ontManager);
        ontmodel = ontManager.getOntology("http://purl.bdrc.io/ontology/admin/", ontSpec);
        bdrcReasoner = BDRCReasoner.getReasoner(ontmodel, owlSchemaBase + "reasoning/kinship.rules", true);

    }

    // @Test
    public void checkRemovedTriples() throws IOException {
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
        Model fatherM = ModelFactory.createDefaultModel();
        Model sonM = ModelFactory.createDefaultModel();
        fatherM.add(fatherR, fatherM.createProperty(BDO, "personGender"), fatherM.createResource(BDR + "GenderMale"));
        sonM.add(sonR, sonM.createProperty(BDO, "personGender"), sonM.createResource(BDR + "GenderMale"));
        System.out.println("we have the father:");
        System.out.println(fatherM.listStatements().toList());
        System.out.println("and the son:");
        System.out.println(sonM.listStatements().toList());
        System.out.println("we receive an update that makes the father look like:");
        Model fatherUpdateM = ModelFactory.createDefaultModel().add(fatherM);
        fatherUpdateM.add(fatherR, hasSon, sonR);
        System.out.println(fatherUpdateM.listStatements().toList());
        System.out.println("we apply the reasoner on non-updated father and get:");
        Model oldInferredModel = ModelFactory.createInfModel(bdrcReasoner, fatherM).getDeductionsModel();
        System.out.println(oldInferredModel.listStatements().toList());
        System.out.println("we apply the reasoner to updated father and get:");
        Model newInferredModel = ModelFactory.createInfModel(bdrcReasoner, fatherUpdateM).getDeductionsModel();
        System.out.println(newInferredModel.listStatements().toList());
        System.out.println("we simply the inferred triples to clean things up a bit and obtain:");
        newInferredModel.remove(BDRCReasoner.deReasonToRemove(ontmodel, newInferredModel));
        System.out.println(newInferredModel.listStatements().toList());
        System.out.println("then we diff that with the original inferred model and select the triples related to the son.");
        Model triplesToRemove = oldInferredModel.difference(newInferredModel);
        Model triplesToAdd = newInferredModel.difference(oldInferredModel);
        SimpleSelector ssSon = new SimpleSelector(sonR, null, (RDFNode) null);
        System.out.println("we should add the following triples to the son model:");
        System.out.println(triplesToAdd.listStatements(ssSon).toList());
        System.out.println("we should remove the following triples from the son model:");
        System.out.println(triplesToRemove.listStatements(ssSon).toList());
    }

}
