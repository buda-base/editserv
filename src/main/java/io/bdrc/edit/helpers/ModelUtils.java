package io.bdrc.edit.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.Prefixes;

public class ModelUtils {

    public static Set<Statement> ModelToSet(Model m) {
        StmtIterator st = m.listStatements();
        List<Statement> stList = IteratorUtils.toList(st);
        return new HashSet<>(stList);
    }

    // All the statements that are in Model A that don't exist in Model B
    public static Set<Statement> ModelComplementAsSet(Model A, Model B) {
        Set<Statement> fullModel = ModelToSet(A);
        Set<Statement> focusModel = ModelToSet(B);
        Set<Statement> differenceSet = new HashSet<Statement>(fullModel);
        differenceSet.removeAll(focusModel);
        return differenceSet;
    }

    // Model with containing the statements that are in Model A and that don't exist
    // in Model B
    public static Model ModelComplement(Model A, Model B) {
        Set<Statement> fullModel = ModelToSet(A);
        Set<Statement> focusModel = ModelToSet(B);
        Set<Statement> differenceSet = new HashSet<Statement>(fullModel);
        differenceSet.removeAll(focusModel);
        Model m = ModelFactory.createDefaultModel();
        m.add(IteratorUtils.toList(differenceSet.iterator()));
        return m;
    }

    public static Set<Statement> mergeModelAsSet(Set<Statement> A, Set<Statement> B) {
        Set<Statement> unionSet = new HashSet<>(A);
        unionSet.addAll(B);
        return unionSet;
    }

    public static Model mergeModel(Model complement, Model focusEdited) {
        Set<Statement> complementModel = ModelToSet(complement);
        Set<Statement> focusModel = ModelToSet(focusEdited);
        Model m = ModelFactory.createDefaultModel();
        return m.add(IteratorUtils.toList(mergeModelAsSet(complementModel, focusModel).iterator()));
    }

    public static Model getEditedModel(Model full, Model focus, Model focusEdited) {
        Model complement = ModelComplement(full, focus);
        return mergeModel(complement, focusEdited);
    }

    // List all objects that are not literals or blank nodes
    public List<RDFNode> getObjectResourceNodes(Model m) {
        List<RDFNode> l = new ArrayList<>();
        NodeIterator nit = m.listObjects();
        while (nit.hasNext()) {
            RDFNode n = nit.next();
            if (n.isURIResource()) {
                l.add(n);
            }
        }
        return l;
    }

    public static String checkToFullUri(String resourceUri) {
        try {
            int prefixIndex = resourceUri.indexOf(":");
            if (prefixIndex < 0) {
                return resourceUri;
            } else {
                String prefix = resourceUri.substring(0, prefixIndex);
                return Prefixes.getFullIRI(prefix) + resourceUri.substring(prefixIndex + 1);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    public static void main(String[] arg) throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        EditConfig.init();
        System.out.println(checkToFullUri("bdr:P1583"));
    }

}
