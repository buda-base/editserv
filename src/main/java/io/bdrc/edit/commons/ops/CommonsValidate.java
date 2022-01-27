package io.bdrc.edit.commons.ops;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.Models;

public class CommonsValidate {

    public static Logger log = LoggerFactory.getLogger(CommonsValidate.class);

    static final String SH = "http://www.w3.org/ns/shacl#";
    public static final String TO_REMOVE = "TO_REMOVE";
    public static final String TO_ADD = "TO_ADD";
    static final Property SH_CONFORMS = ResourceFactory.createProperty(SH + "conforms");
    static final Property SH_RESULT = ResourceFactory.createProperty(SH + "result");
    static final Property SH_VALUE = ResourceFactory.createProperty(SH + "value");
    static final Property LOGENTRY = ResourceFactory.createProperty(Models.ADM + "logEntry");

    static final Literal FALSE = ModelFactory.createDefaultModel().createTypedLiteral(false);
    static final Literal TRUE = ModelFactory.createDefaultModel().createTypedLiteral(true);

    public static Resource getAdmin(final Model m, final String lname) {
        // TODO: do it properly
        return m.createResource(Models.BDA+lname);
    }
    
    // arguments need to be focus graphs
    public static boolean validateCommit(final Model newModel, final Model gitModel, final Resource main) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        // check that the new model is exactly one commit ahead of the git model
        Set<RDFNode> newLogEntries = newModel.listObjectsOfProperty(LOGENTRY).toSet();
        Set<RDFNode> originalLogEntries = new HashSet<>();
        if (gitModel != null)
            originalLogEntries = gitModel.listObjectsOfProperty(LOGENTRY).toSet();
        if (newLogEntries.size() != originalLogEntries.size()+1) {
            log.error("cannot validate model, number of log entries doesn't match: {} vs. {}", newLogEntries.size(), newLogEntries.size());
            return false;
        }
        Set<RDFNode> diff = SetUtils.difference(newLogEntries, originalLogEntries);
        if (diff.size() != 1) {
            log.error("cannot validate model, {} entries not in the original", diff.size());
            return false;
        }
        return true;
    }
    
    public static boolean validateFocusing(final Model unfocused, final Model focused) {
        return unfocused.size() == focused.size();
    }
    
    public static boolean validateNode(Model m, Resource r, Resource shape) {
        // todo: implement
        return true;
    }

    public static String existResource(String resourceUri) {
        String type = null;
        Model m = QueryProcessor.describeModel(resourceUri, null);
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(resourceUri), RDF.type, (RDFNode) null);
        StmtIterator it = m.listStatements(ss);
        while (it.hasNext()) {
            Statement st = it.next();
            type = st.getObject().asResource().getURI();
        }
        return type;
    }

    public static boolean extIsWithdrawn(String resourceUri, boolean prefixed) {
        String shortName = "";
        if (prefixed) {
            shortName = resourceUri.substring(resourceUri.lastIndexOf(":") + 1);
        } else {
            shortName = resourceUri.substring(resourceUri.lastIndexOf("/") + 1);
        }
        String query = "select ?o where { <" + Models.BDA + shortName + "> <" + Models.ADM + "status> ?o }";
        ResultSet rs = QueryProcessor.getSelectResultSet(query, null);
        QuerySolution qs = rs.nextSolution();
        if (qs == null) {
            return true;
        }
        RDFNode n = qs.get("?o");
        if (n.asResource().getURI().equals(Models.BDA + "StatusWithdrawn")) {
            return true;
        }
        return false;
    }

    public static boolean isWithdrawn(Model m, String resourceUri, boolean prefixed) {
        String shortName = "";
        if (prefixed) {
            shortName = resourceUri.substring(resourceUri.lastIndexOf(":") + 1);
        } else {
            shortName = resourceUri.substring(resourceUri.lastIndexOf("/") + 1);
        }
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(Models.BDA + shortName),
                ResourceFactory.createProperty(Models.ADM + "status"), (RDFNode) null);
        StmtIterator it = m.listStatements(ss);
        if (!it.hasNext()) {
            return true;
        }
        RDFNode n = it.next().getObject();
        if (n.asResource().getURI().equals(Models.BDA + "StatusWithdrawn")) {
            return true;
        }
        return false;
    }

    public static boolean validateShacl(final Model m) {
        ValidationReport report = ShaclValidator.get().validate(Shapes.fullShapes, m.getGraph());
        if (report.conforms())
            return true;
        StringBuilder reportStrb = new StringBuilder();
        report.getEntries().forEach(e->{
            reportStrb.append("Node="+ShLib.displayStr(e.focusNode())+"\n");
            if ( e.resultPath() != null )
                reportStrb.append("  Path="+e.resultPath()+"\n");
            if ( e.value() != null )
                reportStrb.append("  Value: "+ShLib.displayStr(e.value())+"\n");
            if ( e.message() != null )
                reportStrb.append("  Message: "+ e.message()+"\n");
        });
        log.error("shacl validation report contains errors, report: \n"+reportStrb.toString());
        return false;
    }

    public static boolean validateExtRIDs(final Model m) {
        // TODO: get all paths of external shapes
        // check that they are in the BDR namespace and that their RID correspond to their expected type
        return true;
    }

    
    public static void main(String[] args) throws Exception {
        EditConfig.init();
        //Model m = CommonsRead.getEditorGraph("P:707");
        //Resource r = validateTQ(m, "bdo:Person");
        // System.out.println(existResource(Models.BDR + "P1583"));

    }

}
