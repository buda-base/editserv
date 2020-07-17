package io.bdrc.edit.commons.data;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.ReasonerVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;

public class OntologyData {

    public final static Logger log = LoggerFactory.getLogger(OntologyData.class.getName());
    static Model ONTOLOGY;
    static Property INVERSE;

    public static void init() {
        System.out.println("Creating prop");
        System.out.println("Created prop");
        EditConfig.init();
        ONTOLOGY = loadOntologyData();
        INVERSE = ONTOLOGY.createProperty("http://www.w3.org/2002/07/owl#", "inverseOf");
    }

    private static Model loadOntologyData() {
        Model m = ModelFactory.createDefaultModel();
        log.info("Ontology data URL {}", EditConfig.getProperty("ontologyDataUrl"));
        m.read(EditConfig.getProperty("ontologyDataUrl"), null, "ttl");
        String rule = "[inverseOf1: (?P owl:inverseOf ?Q) -> (?Q owl:inverseOf ?P) ]";
        List<Rule> miniRules = Rule.parseRules(rule);
        Reasoner reasoner = new GenericRuleReasoner(miniRules);
        reasoner.setParameter(ReasonerVocabulary.PROPruleMode, "forward");
        InfModel core = ModelFactory.createInfModel(reasoner, m);
        m.add(core);
        return m;
        // TODO Auto-generated constructor stub
    }

    public static boolean isSymmetric(String fullPropUri) {
        // if (ONTOLOGY.getSymmetricProperty(fullPropUri) != null) {
        // return true;
        // }
        return false;
    }

    public static OntProperty getInverse(String fullPropUri) {
        // ObjectProperty prop = ONTOLOGY.getObjectProperty(fullPropUri);
        ObjectProperty prop = null;
        OntProperty op = null;
        if (prop != null) {
            // op = ONTOLOGY.getObjectProperty(fullPropUri).getInverseOf();
        }
        if (op == null && prop != null) {
            ExtendedIterator<? extends OntProperty> it = prop.listSuperProperties();
            while (it.hasNext()) {
                OntProperty ontProp = it.next().getInverseOf();
                if (ontProp != null) {
                    return ontProp;
                }
            }
        }
        return op;
    }

    public static List<Statement> getInverseList(String fullPropUri) {
        List<Statement> l = new ArrayList<>();
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), INVERSE, (RDFNode) null);
        StmtIterator it = ONTOLOGY.listStatements(ss);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            l.add(st);
            System.out.println("Object prop : " + st.getObject().asResource().getURI());
            System.out.println("Object prop : " + ONTOLOGY.getProperty(st.getObject().asResource().getURI()));
        }
        ss = new SimpleSelector(null, INVERSE, ResourceFactory.createResource(fullPropUri).asNode());
        it = ONTOLOGY.listStatements(ss);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            l.add(st);
            System.out.println("Subject prop : " + st.getSubject().asResource().getURI());
            System.out.println("Subject prop : " + ONTOLOGY.getProperty(st.getSubject().asResource().getURI()));
        }
        return l;
    }

    public static void main(String[] args) {
        ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#inverseOf");
        EditConfig.init();
        OntologyData.init();
        System.out.println(getInverseList("http://purl.bdrc.io/ontology/core/hasSon"));
        // System.out.println(isSymmetric("http://purl.bdrc.io/ontology/core/kinWith"));
        // log.info("Ontology data URL 1 {}",
        // EditConfig.getProperty("ontologyDataUrl"));
        // OntologyData.ONTOLOGY.write(System.out, "TURTLE");
        // System.out.println(OntologyData.isSymmetric("http://purl.bdrc.io/ontology/core/hasSpouse"));
        // Resource res =
        // ResourceFactory.createResource("http://purl.bdrc.io/ontology/core/partOf");
        // Resource res1 =
        // OntologyData.getInverse("http://purl.bdrc.io/ontology/core/personTeacherOf");
        // System.out.println(res1);
        // System.out.println(res1.getURI().equals(res.getURI()));
    }

}
