package io.bdrc.edit.commons.ops;

import static io.bdrc.libraries.Models.BDG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
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
import io.bdrc.edit.helpers.GitRepo;
import io.bdrc.edit.helpers.GitRepositories;
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

    public static String putAndCommitSingleResource(Model newModel, String prefixedId)
            throws UnknownBdrcResourceException, NotModifiableException, IOException, VersionConflictException,
            ParameterFormatException, ValidationException, InvalidRemoteException, TransportException, GitAPIException {
        String resType = CommonsRead.getFullResourceTypeUri(prefixedId, newModel, false);
        Model current = CommonsGit.getGraphFromGit(prefixedId);
        // at this point, there's no conflict and the newModel is validated.
        // we are now merging the new model and the current one to apply the changes
        Model merged = ModelUtils.mergeModel(current, newModel);
        // Now writing the changes -merged model- to the local git repo
        // Getting admin data
        Model admin = merged.getResource(Models.BDA + prefixedId).getModel();
        String gitPath = null;
        String gitRepo = null;
        NodeIterator ni = admin.listObjectsOfProperty(EditConstants.GIT_PATH);
        if (ni.hasNext()) {
            gitPath = ni.next().asLiteral().getString();
        }
        ni = admin.listObjectsOfProperty(EditConstants.GIT_REPO);
        if (ni.hasNext()) {
            gitRepo = ni.next().asResource().getLocalName();
        }
        log.info("Local Git root: {} gitRepo is {} and gitPath is {}", EditConfig.getProperty("gitLocalRoot"),
                Models.BDA + gitRepo, gitPath);
        log.info("Gitrepository object {}", GitRepositories.getRepoByUri(Models.ADM + gitRepo));
        FileOutputStream fos = new FileOutputStream(EditConfig.getProperty("gitLocalRoot")
                + GitRepositories.getRepoByUri(Models.BDA + gitRepo).getGitRepoName() + "/" + gitPath);
        modelToOutputStream(merged, fos, Models.BDR + (prefixedId.substring(prefixedId.lastIndexOf(":") + 1)));
        RevCommit rev = GitHelpers.commitChanges(resType.substring(resType.lastIndexOf("/") + 1).toLowerCase(),
                "Committed model :" + prefixedId);
        if (rev != null) {
            GitHelpers.push(resType.substring(resType.lastIndexOf("/") + 1).toLowerCase(),
                    EditConfig.getProperty("gitRemoteBase"), EditConfig.getProperty("gitUser"),
                    EditConfig.getProperty("gitPass"), EditConfig.getProperty("gitLocalRoot"));
            return rev.getName();
        }
        return null;
    }

    public static Model getGraphFromGit(String graphUri)
            throws UnknownBdrcResourceException, NotModifiableException, IOException {
        String rootId = "";
        if (graphUri.indexOf("/") > 0 && !graphUri.startsWith(Models.BDR)) {
            throw new UnknownBdrcResourceException(graphUri + " is not a BDRC resource Uri");
        }
        if (graphUri.indexOf("/") == -1 && !graphUri.startsWith("bdr:")) {
            throw new UnknownBdrcResourceException(graphUri + " is not a BDRC resource Uri");
        }
        if (graphUri.indexOf("/") > 0) {
            rootId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        }
        if (graphUri.indexOf("/") == -1) {
            rootId = graphUri.substring(graphUri.lastIndexOf(":") + 1);
        }
        log.info("Getting graph for {} ", Models.BDG + rootId);
        Model m = QueryProcessor.getGraph(Models.BDG + rootId, null);
        NodeIterator g_path = m.listObjectsOfProperty(EditConstants.GIT_PATH);
        String gitPath = null;
        if (g_path.hasNext()) {
            gitPath = g_path.next().asLiteral().getString();
        }
        NodeIterator g_repo = m.listObjectsOfProperty(EditConstants.GIT_REPO);
        String gitRepo = null;
        if (g_repo.hasNext()) {
            gitRepo = g_repo.next().asResource().getURI();
        }
        if (gitPath == null || gitRepo == null) {
            throw new NotModifiableException(
                    graphUri + " is not a modifiable BDRC resource - gitPath=" + gitPath + " and gitRepo=" + gitRepo);
        }
        log.info("Local Git root: {} gitRepo is {} and gitPath is {}", EditConfig.getProperty("gitLocalRoot"),
                Models.BDA + gitRepo, gitPath);

        GitRepo repo = GitRepositories.getRepoByUri(gitRepo);
        return ModelFactory.createModelForGraph(Helpers
                .buildGraphFromTrig(GlobalHelpers.readFileContent(
                        EditConfig.getProperty("gitLocalRoot") + repo.getGitRepoName() + "/" + gitPath))
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
