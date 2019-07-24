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

import io.bdrc.edit.EditConstants;

public class GitRepositories {

    public static String GIT_REPO_TYPES_FILE_URL = "https://raw.githubusercontent.com/buda-base/owl-schema/master/adm/types/git_repos.ttl";

    public static HashMap<String, GitRepo> repos;

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
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void loadRepos(OntModel mod) {
        repos = new HashMap<>();
        ExtendedIterator<Individual> ind = mod.listIndividuals();
        while (ind.hasNext()) {
            Individual i = ind.next();
            String name = i.getProperty(ResourceFactory.createProperty(EditConstants.ADM + "gitRepoName")).getObject().toString();
            String url = i.getProperty(ResourceFactory.createProperty(EditConstants.ADM + "gitUrl")).getObject().toString();
            GitRepo rep = new GitRepo(i.getURI(), name, url);
            repos.put(rep.getRepoResType(), rep);
        }
    }

    public static GitRepo getRepo(String resType) {
        // System.out.println(repos);
        return repos.get(resType);
    }

}
