package io.bdrc.edit.commons;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.GitRepo;
import io.bdrc.edit.helpers.GitRepositories;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Models;

public class CommonsRead {

    public final static Logger log = LoggerFactory.getLogger("default");
    public static String SHAPES_ROOT_URI = "http://purl.bdrc.io/ontology/shapes/core/";

    public static DatasetGraph getGraph(String graphUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        if (!graphUri.startsWith(Models.BDR)) {
            throw new UnknownBdrcResourceException(graphUri + " is not a BDRC resource Uri");
        }
        String rootId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        Model m = QueryProcessor.getGraph(Models.BDG + rootId);
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
            throw new NotModifiableException(graphUri + " is not a modifiable BDRC resource - gitPath=" + gitPath + " and gitRepo=" + gitRepo);
        }
        GitRepo repo = GitRepositories.getRepoByUri(gitRepo);
        return Helpers
                .buildGraphFromTrig(GlobalHelpers.readFileContent(EditConfig.getProperty("gitLocalRoot") + repo.getGitRepoName() + "/" + gitPath));
    }

    public static Model getShapesForType(String prefixedType) throws IOException, ParameterFormatException {
        if (!prefixedType.startsWith("bdo") || prefixedType.indexOf(":") == -1) {
            throw new ParameterFormatException(prefixedType + " is not a valid prefixed bdo type");
        }
        String url = SHAPES_ROOT_URI.replace("purl.bdrc.io", EditConfig.getProperty("shapeServerRoot"))
                + prefixedType.substring(prefixedType.lastIndexOf(":") + 1) + "Shapes.ttl";
        log.info("getting shape from url {}", url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        InputStream stream = connection.getInputStream();
        Model mod = ModelFactory.createDefaultModel();
        mod.read(stream, null, "TTL");
        stream.close();
        return mod;
    }

    public static void main(String[] arg) throws IOException, ParameterFormatException {
        EditConfig.init();
        Model m = getShapesForType("bdo:Person");
        m.write(System.out, "TURTLE");
    }

}
