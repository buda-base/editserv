package io.bdrc.edit.helpers;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
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
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.libraries.BDRCReasoner;
import io.bdrc.libraries.GitHelpers;
import io.bdrc.libraries.SparqlCommons;

public class BulkOps {

	public final static Logger log = LoggerFactory.getLogger(BulkOps.class.getName());

	public static String STATUS_PROP = "http://purl.bdrc.io/ontology/admin/status";
	public static String STATUS_WITHDRAWN = "http://purl.bdrc.io/admindata/StatusWithdrawn";
	public static String STATUS_RELEASED = "http://purl.bdrc.io/admindata/StatusReleased";
	public static Reasoner REASONER = BDRCReasoner.getReasoner();

	public static Model mergeGraphsOfSameResourceType(String to_mergeGraphUri, String to_keepGraphUri, String resourceType)
			throws NoSuchAlgorithmException, IOException {
		Model merged = ModelFactory.createDefaultModel();
		String to_mergeResId = to_mergeGraphUri.substring(to_mergeGraphUri.lastIndexOf("/") + 1);
		AdminData ad_to_merge = new AdminData(to_mergeResId, resourceType);
		String to_keepResId = to_keepGraphUri.substring(to_keepGraphUri.lastIndexOf("/") + 1);
		AdminData ad_to_keep = new AdminData(to_keepResId, resourceType);
		Model to_merge = ModelFactory.createModelForGraph(Helpers
				.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(
						EditConfig.getProperty("gitLocalRoot") + ad_to_merge.getGitRepo().getGitRepoName(), ad_to_merge.getGitPath()))
				.getUnionGraph());
		Model to_keep = ModelFactory
				.createModelForGraph(Helpers
						.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(
								EditConfig.getProperty("gitLocalRoot") + ad_to_keep.getGitRepo().getGitRepoName(), ad_to_keep.getGitPath()))
						.getUnionGraph());
		to_merge = BulkOps.inModelReplace(to_merge, to_mergeGraphUri, to_keepGraphUri);
		merged.add(to_merge);
		merged.add(to_keep);
		return merged;
	}

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

	public static void replaceAllDuplicateByValid(String replacedUri, String validUri, String fusekiUrl)
			throws DataUpdateException, NoSuchAlgorithmException, IOException, InvalidRemoteException, TransportException, GitAPIException {
		String gitUser = EditConfig.getProperty("gitUser");
		String gitPass = EditConfig.getProperty("gitPass");
		if (fusekiUrl == null) {
			if (fusekiUrl == null) {
				fusekiUrl = EditConfig.getProperty(EditConfig.FUSEKI_URL);
			}
		}
		HashMap<String, ArrayList<String>> map = SparqlCommons.getGraphsForResourceByGitRepos(replacedUri, fusekiUrl);
		HashMap<String, Model> models = new HashMap<>();
		String replacedResId = replacedUri.substring(replacedUri.lastIndexOf("/") + 1);
		Set<String> repos = map.keySet();
		for (String rep : repos) {
			ArrayList<String> affectedGraphs = map.get(rep);
			GitRepo gitRep = GitRepositories.getRepoByUri(rep);
			System.out.println("GitRep for uri: " + rep + " is :" + gitRep);
			AdminData ad = null;
			Model to_update = null;
			for (String uri : affectedGraphs) {
				if (!uri.endsWith(replacedResId)) {
					System.out.println("Processing Uri: " + uri);
					ad = new AdminData(uri.substring(uri.lastIndexOf("/") + 1), gitRep.getRepoResType());
					// Building model from git
					to_update = ModelFactory
							.createModelForGraph(Helpers
									.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(
											EditConfig.getProperty("gitLocalRoot") + ad.getGitRepo().getGitRepoName(), ad.getGitPath()))
									.getUnionGraph());
					// Updating model
					to_update = SparqlCommons.replaceRefInModel(to_update, uri, replacedUri, validUri, null);

				} else {
					// if the graph being processed is the graph of the withdrawn resource, the
					// update status in admindata object
					ad = new AdminData(uri.substring(uri.lastIndexOf("/") + 1), gitRep.getRepoResType());
					// Building model from git
					to_update = ModelFactory.createModelForGraph(Helpers
							.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(
									EditConfig.getProperty("gitLocalRoot") + ad.getGitRepo().getGitRepoName(), ad.getGitPath()))
							.getGraph(NodeFactory.createURI(uri)));
					// Updating status
					SparqlCommons.setAdminStatus(to_update, ad.getUri(), STATUS_WITHDRAWN);
					to_update.write(System.out, "TURTLE");
				}
				models.put(uri, to_update);
				// writing the graph back to git
				FileOutputStream fos = new FileOutputStream(
						EditConfig.getProperty("gitLocalRoot") + ad.getGitRepo().getGitRepoName() + "/" + ad.getGitPath());
				Helpers.modelToOutputStream(to_update, fos, uri.substring(uri.lastIndexOf("/") + 1));
			}
			// Commit changes to the repo
			GitHelpers.ensureGitRepo(gitRep.getRepoResType(), EditConfig.getProperty("gitLocalRoot"));
			RevCommit rev = GitHelpers.commitChanges(gitRep.getRepoResType(), "Committed by marc" + " for replaceduri:" + replacedUri);
			if (rev != null) {
				GitHelpers.push(gitRep.getRepoResType(), EditConfig.getProperty("gitRemoteBase"), gitUser, gitPass,
						EditConfig.getProperty("gitLocalRoot"));
			} else {
				DataUpdateException due = new DataUpdateException("Commit failed in repo :" + gitRep.getGitRepoName());
				log.error("Commit failed in repo :" + gitRep.getGitRepoName(), due);
				throw new DataUpdateException("Commit failed in repo :" + gitRep.getGitRepoName());
			}
			// for a given repo, set git revision number then update fuseki with the
			// corresponding updated models/graphs
			Set<String> set = models.keySet();
			RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
			RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
			for (String graphUri : set) {
				Model m = models.get(graphUri);
				m = SparqlCommons.setGitRevision(m, "http://purl.bdrc.io/admindata/" + graphUri.substring(graphUri.lastIndexOf("/") + 1),
						rev.getName());
				Helpers.putModelWithInference(fusConn, graphUri, m, REASONER);
			}
			fusConn.close();
		}

	}

	public static void main(String[] args)
			throws NoSuchAlgorithmException, InvalidRemoteException, TransportException, IOException, GitAPIException, DataUpdateException {
		EditConfig.init();
		replaceAllDuplicateByValid("http://purl.bdrc.io/resource/P1595", "http://purl.bdrc.io/resource/PPP1595",
				"http://buda1.bdrc.io:13180/fuseki/testrw/query");
	}

}
