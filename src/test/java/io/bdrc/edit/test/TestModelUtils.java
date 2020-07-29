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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.Reasoner;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsValidate;
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
    static Model initial = ModelFactory.createDefaultModel();
    static Model missingSon = ModelFactory.createDefaultModel();
    static Model moreSon = ModelFactory.createDefaultModel();
    static Model edited = ModelFactory.createDefaultModel();

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.initForTests(null);
        OntologyData.init();
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
        InputStream in = TestModelUtils.class.getClassLoader().getResourceAsStream("P705.ttl");
        initial.read(in, null, "TTL");
        in = TestModelUtils.class.getClassLoader().getResourceAsStream("P705_missingSon.ttl");
        missingSon.read(in, null, "TTL");
        in = TestModelUtils.class.getClassLoader().getResourceAsStream("P705_oneMoreSon.ttl");
        moreSon.read(in, null, "TTL");
        in = TestModelUtils.class.getClassLoader().getResourceAsStream("P705_edited.ttl");
        edited.read(in, null, "TTL");
    }

    @Test
    public void findAllTriplesToProcessFromModels() throws IOException, UnknownBdrcResourceException, NotModifiableException {
        findTriplesToProcess(initial, edited);
    }

    @Test
    public void findTriplesToRemoveFromModels() throws IOException, UnknownBdrcResourceException, NotModifiableException {
        findTriplesToProcess(initial, missingSon);
    }

    @Test
    public void findTriplesToAddFromModels() throws IOException, UnknownBdrcResourceException, NotModifiableException {
        findTriplesToProcess(initial, moreSon);
    }

    public static void findTriplesToProcess(Model init, Model edit) throws IOException, UnknownBdrcResourceException, NotModifiableException {
        System.out.println("we apply the reasoner on non-updated father and get:");
        Model oldInferredModel = ModelFactory.createInfModel(bdrcReasoner, init).getDeductionsModel();
        oldInferredModel.remove(BDRCReasoner.deReasonToRemove(ontmodel, oldInferredModel));
        System.out.println(oldInferredModel.listStatements().toList());
        System.out.println("we apply the reasoner to updated father and get:");
        Model newInferredModel = ModelFactory.createInfModel(bdrcReasoner, edit).getDeductionsModel();
        System.out.println(newInferredModel.listStatements().toList());
        System.out.println("we simplify the inferred triples to clean things up a bit and obtain:");
        newInferredModel.remove(BDRCReasoner.deReasonToRemove(ontmodel, newInferredModel));
        Model triplesToRemove = oldInferredModel.difference(newInferredModel);
        Model triplesToAdd = newInferredModel.difference(oldInferredModel);
        System.out.println("-------------------------------------------");
        System.out.println("These were removed by the editor:");
        Set<Statement> diff = CommonsValidate.getDiffRemovedTriples(init, edit);
        System.out.println("-------------------------------------------");
        for (Statement d : diff) {
            System.out.println("triple removed by edition -->: " + d);
        }
        Set<Statement> adds = CommonsValidate.getDiffAddedTriples(init, edit);
        System.out.println("-------------------------------------------");
        for (Statement ad : adds) {
            System.out.println("triple added by edition -->: " + ad);
        }
        System.out.println("-------------------------------------------");
        System.out.println("we should add the following triples to the son model:");
        System.out.println("-------------------------------------------");
        List<Statement> stadd = triplesToAdd.listStatements().toList();
        for (Statement add : stadd) {
            if (add.getObject().asNode().getURI().equals("http://purl.bdrc.io/resource/P705")) {
                System.out.println("triples To Add -->: " + add);
            }
        }
        System.out.println("-------------------------------------------");
        System.out.println("we should remove the following triples from the son model:");
        System.out.println("-------------------------------------------");
        List<Statement> strem = triplesToRemove.listStatements().toList();
        for (Statement rem : strem) {
            if (rem.getObject().asNode().getURI().equals("http://purl.bdrc.io/resource/P705")) {
                System.out.println("triple To Remove -->: " + rem);
            }
        }
        System.out.println("-------------------------------------------");
    }

}
