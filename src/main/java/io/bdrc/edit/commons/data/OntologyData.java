package io.bdrc.edit.commons.data;

import java.util.List;

import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
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
    static OntModel ONTOLOGY;

    public static void init() {
        EditConfig.init();
        ONTOLOGY = loadOntologyData();
    }

    private static OntModel loadOntologyData() {
        OntModel m = ModelFactory.createOntologyModel();
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
        if (ONTOLOGY.getSymmetricProperty(fullPropUri) != null) {
            return true;
        }
        return false;
    }

    public static OntProperty getInverse(String fullPropUri) {
        ObjectProperty prop = ONTOLOGY.getObjectProperty(fullPropUri);
        OntProperty op = ONTOLOGY.getObjectProperty(fullPropUri).getInverseOf();
        if (op == null) {
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

    public static void main(String[] args) {
        EditConfig.init();
        OntologyData.init();
        System.out.println(getInverse("http://purl.bdrc.io/ontology/core/hasSon"));
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
