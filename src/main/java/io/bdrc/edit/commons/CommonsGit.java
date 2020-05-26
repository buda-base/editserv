package io.bdrc.edit.commons;

import static io.bdrc.libraries.Models.BDG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.sparql.core.DatasetGraph;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.GitRepositories;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.edit.txn.exceptions.ValidationException;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.GitHelpers;
import io.bdrc.libraries.Models;

public class CommonsGit {

    public static String putResource(Model newModel, String prefixedId) throws UnknownBdrcResourceException, NotModifiableException, IOException,
            VersionConflictException, ParameterFormatException, ValidationException, InvalidRemoteException, TransportException, GitAPIException {
        String prefixedResType = CommonsRead.getResourceTypeUri(prefixedId, newModel, true);
        Model current = CommonsRead.getGraphFromGit(prefixedId);
        if (!CommonsValidate.validateCommit(newModel, prefixedId)) {
            throw new VersionConflictException("Version conflict while trying to save " + prefixedId);
        }
        if (!CommonsValidate.validateShacl(newModel, prefixedId)) {
            throw new ValidationException("Could not validate " + prefixedId);
        }
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
        FileOutputStream fos = new FileOutputStream(
                EditConfig.getProperty("gitLocalRoot") + GitRepositories.getRepoByUri(Models.ADM + gitRepo).getGitRepoName() + "/" + gitPath);
        modelToOutputStream(merged, fos, Models.BDR + (prefixedId.substring(prefixedId.lastIndexOf(":") + 1)));
        RevCommit rev = GitHelpers.commitChanges(prefixedResType.substring(prefixedResType.lastIndexOf(":") + 1).toLowerCase(),
                "Committed model :" + prefixedId);
        if (rev != null) {
            GitHelpers.push(prefixedResType.substring(prefixedResType.lastIndexOf(":") + 1).toLowerCase(), EditConfig.getProperty("gitRemoteBase"),
                    EditConfig.getProperty("gitUser"), EditConfig.getProperty("gitPass"), EditConfig.getProperty("gitLocalRoot"));
            return rev.getName();
        }
        return null;
    }

    private static void modelToOutputStream(Model m, OutputStream out, String resId) throws IOException {
        // m = removeGitInfo(m);
        String uriStr = BDG + resId;
        Node graphUri = NodeFactory.createURI(uriStr);
        DatasetGraph dsg = DatasetFactory.create().asDatasetGraph();
        dsg.addGraph(graphUri, m.getGraph());
        new STriGWriter().write(out, dsg, Helpers.getPrefixMap(), graphUri.toString(m), Helpers.createWriterContext());
        out.close();
    }

}
