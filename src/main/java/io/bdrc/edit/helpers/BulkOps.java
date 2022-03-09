
package io.bdrc.edit.helpers;

import java.util.HashMap;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.reasoner.Reasoner;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.libraries.BDRCReasoner;
import io.bdrc.libraries.GitHelpers;
import io.bdrc.libraries.SparqlCommons;

public class BulkOps {

    public final static Logger log = LoggerFactory.getLogger(BulkOps.class.getName());

    public static String STATUS_PROP = "http://purl.bdrc.io/ontology/admin/status";
    public static String STATUS_WITHDRAWN = "http://purl.bdrc.io/admindata/StatusWithdrawn";
    public static String STATUS_RELEASED = "http://purl.bdrc.io/admindata/StatusReleased";
    public static Reasoner REASONER = BDRCReasoner.getReasoner(OntologyData.ONTOLOGY,
            EditConfig.getProperty("owlSchemaBase") + "reasoning/kinship.rules", true);


    public static Model inModelReplace(Model mod, String uriOld, String uriNew) {
        StmtIterator it = mod.listStatements();
        while (it.hasNext()) {
            Statement smt = it.next();
            if (smt.getSubject().getURI().equals(uriOld)) {
                Statement st = mod.createStatement(ResourceFactory.createResource(uriNew), smt.getPredicate(), smt.getObject());
                mod.add(st);
                mod.remove(smt);
            }
            if (smt.getObject().isURIResource()) {
                if (smt.getSubject().getURI().equals(uriOld)) {
                    Statement st = mod.createStatement(smt.getSubject(), smt.getPredicate(), ResourceFactory.createResource(uriNew));
                    mod.add(st);
                    mod.remove(smt);
                }
            }
        }
        return mod;
    }

    private static void updateFuseki(HashMap<String, Model> models, String revision) {
        Set<String> set = models.keySet();
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        for (String graphUri : set) {
            Model m = models.get(graphUri);
            m = SparqlCommons.setGitRevision(m, "http://purl.bdrc.io/admindata/" + graphUri.substring(graphUri.lastIndexOf("/") + 1), revision);
            Helpers.putModelWithInference(fusConn, graphUri, m, REASONER);
        }
        fusConn.close();
    }

    private static RevCommit commitRepo(String repoPath) throws InvalidRemoteException, TransportException, GitAPIException, ModuleException {
        String gitUser = EditConfig.getProperty("gitUser");
        String gitPass = EditConfig.getProperty("gitPass");
        final String type = repoPath.substring(repoPath.length()-2);
        GitHelpers.ensureGitRepo(type, EditConfig.getProperty("gitLocalRoot"));
        RevCommit rev = GitHelpers.commitChanges(type, "Committed by editserv");
        if (rev != null) {
            GitHelpers.push(type, EditConfig.getProperty("gitRemoteBase"), gitUser, gitPass,
                    EditConfig.getProperty("gitLocalRoot"));
        } else {
            ModuleException due = new ModuleException("Commit failed in repo :" + repoPath);
            log.error("Commit failed in repo :" + repoPath, due);
            throw due;
        }
        return rev;
    }

}
