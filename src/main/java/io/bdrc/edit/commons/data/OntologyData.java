package io.bdrc.edit.commons.data;

import java.util.ArrayList;
import java.util.List;

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
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.ReasonerVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;

public class OntologyData {

    public final static Logger log = LoggerFactory.getLogger(OntologyData.class.getName());
    static Model ONTOLOGY;
    static Property INVERSE;

    public static void init() {
        ONTOLOGY = loadOntologyData();
        INVERSE = ONTOLOGY.createProperty("http://www.w3.org/2002/07/owl#", "inverseOf");
    }

    private static Model loadOntologyData() {
        Model m = ModelFactory.createDefaultModel();
        log.info("Ontology data URL {}", EditConfig.getProperty("ontologyDataUrl"));
        m.read(EditConfig.getProperty("ontologyDataUrl"), null, "ttl");
        String rule = "[inverseOf1: (?P owl:inverseOf ?Q) -> (?Q owl:inverseOf ?P) ] [inv:  (?a rdfs:subPropertyOf ?b), (?b owl:inverseOf ?c) -> (?a owl:inverseOf ?c)]";
        List<Rule> miniRules = Rule.parseRules(rule);
        Reasoner reasoner = new GenericRuleReasoner(miniRules);
        reasoner.setParameter(ReasonerVocabulary.PROPruleMode, "hybrid");
        InfModel core = ModelFactory.createInfModel(reasoner, m);
        m.add(core);
        return m;
    }

    public static boolean isSymmetric(String fullPropUri) {
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), RDF.type,
                ResourceFactory.createResource("http://www.w3.org/2002/07/owl#SymmetricProperty"));
        StmtIterator it = ONTOLOGY.listStatements(ss);
        if (it.hasNext()) {
            System.out.println(it.next());
            return true;
        }
        return false;
    }

    public static List<Statement> getInverseListStatement(String fullPropUri) {
        List<Statement> l = new ArrayList<>();
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), INVERSE, (RDFNode) null);
        StmtIterator it = ONTOLOGY.listStatements(ss);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            l.add(st);
        }
        return l;
    }

    public static List<Property> getInverseListProperty(String fullPropUri) {
        List<Property> l = new ArrayList<>();
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), INVERSE, (RDFNode) null);
        StmtIterator it = ONTOLOGY.listStatements(ss);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            Property p = ONTOLOGY.getProperty(st.getObject().asResource().getURI());
            if (!l.contains(p)) {
                l.add(p);
            }
        }
        return l;
    }

    public static void main(String[] args) {
        ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#inverseOf");
        EditConfig.init();
        OntologyData.init();
        System.out.println("<-----------Inverseof hasSon------------>");
        System.out.println(getInverseListStatement("http://purl.bdrc.io/ontology/core/hasSon"));
        System.out.println(getInverseListProperty("http://purl.bdrc.io/ontology/core/hasSon"));
        System.out.println(isSymmetric("http://purl.bdrc.io/ontology/core/kinWith1"));
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
