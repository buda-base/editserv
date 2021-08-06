package io.bdrc.edit.commons.ops;

import static io.bdrc.libraries.Models.BDG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.core.DatasetGraph;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.edit.txn.exceptions.ValidationException;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GitHelpers;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Models;

public class CommonsGit {

    public static Logger log = LoggerFactory.getLogger(CommonsGit.class);
    
    public final static Map<String,String> prefixToRepoPath = new HashMap<>();
    static {
        prefixToRepoPath.put("WAS", "works");
        prefixToRepoPath.put("ITW", "items");
        prefixToRepoPath.put("PRA", "subscribers");
        prefixToRepoPath.put("WA", "works");
        prefixToRepoPath.put("MW", "instances");
        prefixToRepoPath.put("PR", "collections");
        prefixToRepoPath.put("IE", "einstances");
        //prefixToRepoPath.put("UT", "etexts");
        prefixToRepoPath.put("IT", "items");
        prefixToRepoPath.put("W", "iinstances");
        prefixToRepoPath.put("P", "persons");
        prefixToRepoPath.put("G", "places");
        prefixToRepoPath.put("R", "roles");
        prefixToRepoPath.put("L", "lineages");
        prefixToRepoPath.put("C", "corporations");
        prefixToRepoPath.put("T", "topics");
        prefixToRepoPath.put("U", "users");
    }

    public final static Map<String,String> gitIdToRepoPath = new HashMap<>();
    static {
        gitIdToRepoPath.put("GR0008", "works");
        gitIdToRepoPath.put("GR0015", "subscribers");
        gitIdToRepoPath.put("GR0012", "instances");
        gitIdToRepoPath.put("GR0011", "collections");
        gitIdToRepoPath.put("GR0013", "einstances");
        //gitIdToRepoPath.put("GR0002", "etexts");
        gitIdToRepoPath.put("GR0003", "items");
        gitIdToRepoPath.put("GR0014", "iinstances");
        gitIdToRepoPath.put("GR0006", "persons");
        gitIdToRepoPath.put("GR0005", "places");
        gitIdToRepoPath.put("GR0010", "roles");
        gitIdToRepoPath.put("GR0004", "lineages");
        gitIdToRepoPath.put("GR0001", "corporations");
        gitIdToRepoPath.put("GR0007", "topics");
    }
    
    public static String gitPathFromLname(final String lname) {
        final String typePrefix = RIDController.getTypePrefix(lname);
        if (typePrefix == null)
            return null;
        final String md5 = Models.getMd5(lname);
        final String repoPath = prefixToRepoPath.get(typePrefix);
        return repoPath+"/"+md5+"/"+lname+".trig";
    }
    
    public static String gitPathFromAdmin(final Resource admin) {
        String gitPath = null;
        String gitRepoLname = null;
        StmtIterator ni = admin.listProperties(EditConstants.GIT_PATH);
        if (ni.hasNext()) {
            gitPath = ni.next().getObject().asLiteral().getString();
        }
        ni = admin.listProperties(EditConstants.GIT_REPO);
        if (ni.hasNext()) {
            gitRepoLname = ni.next().getObject().asResource().getLocalName();
        }
        final String gitRepoPath = gitIdToRepoPath.get(gitRepoLname);
        return gitRepoPath+"/"+gitPath;
    }
    
    public static String getRevFromLatestLogEntry(final Model newModel, final String lname) {
        // TODO: read the log entries, etc.
        return "updating "+lname;
    }
    
    public static String putAndCommitSingleResource(final Model newModel, final String lname)
            throws UnknownBdrcResourceException, NotModifiableException, IOException, VersionConflictException,
            ParameterFormatException, ValidationException, InvalidRemoteException, TransportException, GitAPIException {
        final String gitPath = gitPathFromLname(lname);
        final String typePrefix = RIDController.getTypePrefix(lname);
        final String repoPath = prefixToRepoPath.get(typePrefix);
        final Model current = ModelFactory.createModelForGraph(Helpers
                .buildGraphFromTrig(GlobalHelpers.readFileContent(
                        EditConfig.getProperty("gitLocalRoot") + gitPath))
                .getUnionGraph());;
        final Model merged = ModelUtils.mergeModel(current, newModel);
        FileOutputStream fos = new FileOutputStream(EditConfig.getProperty("gitLocalRoot") + gitPath);
        modelToOutputStream(merged, fos, Models.BDR + lname);
        RevCommit rev = GitHelpers.commitChanges(repoPath, getRevFromLatestLogEntry(newModel, lname));
        if (rev != null) {
            GitHelpers.push(repoPath,
                    EditConfig.getProperty("gitRemoteBase"), EditConfig.getProperty("gitUser"),
                    EditConfig.getProperty("gitPass"), EditConfig.getProperty("gitLocalRoot"));
            return rev.getName();
        }
        return null;
    }
    
    public static Model getModelFromFuseki(final String graphLname) {
        final Model m = QueryProcessor.getGraph(Models.BDG + graphLname, null);
        return m;
    }

    public static Model getGraphFromGit(final String lname)
            throws UnknownBdrcResourceException, NotModifiableException, IOException {
        final String gitPath = gitPathFromLname(lname);
        return ModelFactory.createModelForGraph(Helpers
                .buildGraphFromTrig(GlobalHelpers.readFileContent(
                        EditConfig.getProperty("gitLocalRoot") + gitPath))
                .getUnionGraph());
    }

    private static void modelToOutputStream(Model m, OutputStream out, String resId) throws IOException {
        // m = removeGitInfo(m);
        String uriStr = BDG + resId;
        Node graphUri = NodeFactory.createURI(uriStr);
        DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
        dsg.addGraph(graphUri, m.getGraph());
        new STriGWriter().write(out, dsg, EditConfig.prefix.getPrefixMap(), graphUri.toString(m), Helpers.createWriterContext());
        out.close();
    }

}
