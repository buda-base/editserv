package io.bdrc.edit.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConstants;

public class GitRepositories {

    public static String GIT_REPO_TYPES_FILE_URL = "https://raw.githubusercontent.com/buda-base/owl-schema/master/adm/types/git_repos.ttl";

    public static HashMap<String, GitRepo> reposByType;
    public static HashMap<String, GitRepo> reposByUri;
    public final static Logger log = LoggerFactory.getLogger(GitRepositories.class.getName());

    static {

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(GIT_REPO_TYPES_FILE_URL).openConnection();
            InputStream stream;
            stream = connection.getInputStream();
            final Model reposMod = ModelFactory.createDefaultModel();
            reposMod.read(stream, "", "TURTLE");
            stream.close();
            OntModel om = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, reposMod);
            loadRepos(om);
        } catch (IOException e) {
            log.error("GitRepositories failed to initialize", e);
        }
    }

    private static void loadRepos(OntModel mod) {
        reposByType = new HashMap<>();
        reposByUri = new HashMap<>();
        ExtendedIterator<Individual> ind = mod.listIndividuals();
        while (ind.hasNext()) {
            Individual i = ind.next();
            String name = i.getProperty(ResourceFactory.createProperty(EditConstants.ADM + "gitRepoName")).getObject().toString();
            String url = i.getProperty(ResourceFactory.createProperty(EditConstants.ADM + "gitUrl")).getObject().toString();
            GitRepo rep = new GitRepo(i.getURI(), name, url);
            reposByType.put(rep.getRepoResType(), rep);
            // System.out.println("GitRepositories adding :"+rep + " to )
            reposByUri.put(i.getURI(), rep);
        }
    }

    public static GitRepo getRepoByType(String resType) {
        return reposByType.get(resType);
    }

    public static GitRepo getRepoByUri(String repUri) {
        return reposByUri.get(repUri);
    }

}
