package io.bdrc.edit.commons;

import java.io.IOException;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.sparql.core.DatasetGraph;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.GitRepo;
import io.bdrc.edit.helpers.GitRepositories;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Models;

public class CommonsRead {

    public static DatasetGraph getGraph(String graphUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        if (graphUri.startsWith(Models.BDR)) {
            throw new UnknownBdrcResourceException(graphUri + " is not a BDRC resource Uri");
        }
        String rootId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        Dataset ds = DatasetFactory.assemble(QueryProcessor.getGraph(graphUri));
        String query = "construct { ?s ?p ?o } where { graph <" + Models.BDA + rootId + "> { ?s ?p ?o }}";
        QueryExecution qe = QueryExecutionFactory.create(query, ds);
        Dataset res = qe.execConstructDataset();
        NodeIterator g_path = res.getUnionModel().listObjectsOfProperty(EditConstants.GIT_PATH);
        String gitPath = null;
        if (g_path.hasNext()) {
            gitPath = g_path.next().asLiteral().getString();
        }
        NodeIterator g_repo = res.getUnionModel().listObjectsOfProperty(EditConstants.GIT_REPO);
        String gitRepo = null;
        if (g_repo.hasNext()) {
            gitRepo = g_repo.next().asLiteral().getString();
        }
        if (gitPath == null || gitRepo == null) {
            throw new NotModifiableException(graphUri + " is not a modifiable BDRC resource");
        }
        GitRepo repo = GitRepositories.getRepoByUri(gitRepo);
        return Helpers.buildGraphFromTrig(GlobalHelpers
                .readFileContent(EditConfig.getProperty("gitLocalRoot") + repo.getGitRepoName() + "/" + gitPath + "/" + rootId + ".trig"));
    }

}
