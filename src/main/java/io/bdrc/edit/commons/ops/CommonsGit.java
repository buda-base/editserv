package io.bdrc.edit.commons.ops;

import static io.bdrc.libraries.Models.BDG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
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
    
    public static final class GitInfo {
        public String repoLname = null;
        public String pathInRepo = null;
        public String revId = null;
        // in the case of a new resource ,the dataset doesn't exist and is null
        public Dataset ds = null;
    }
    
    public final static Map<String,String> prefixToGitLname = new HashMap<>();
    public final static Map<String,String> gitLnameToRepoPath = new HashMap<>();
    
    public static void init() {
        gitLnameToRepoPath.put("GR0100", EditConfig.getProperty("usersGitLocalRoot"));
        prefixToGitLname.put("U", "GR0100");
        final String normalPath = EditConfig.getProperty("gitLocalRoot");
        gitLnameToRepoPath.put("GR0008", normalPath+"works");
        gitLnameToRepoPath.put("GR0015", normalPath+"subscribers");
        gitLnameToRepoPath.put("GR0012", normalPath+"instances");
        gitLnameToRepoPath.put("GR0011", normalPath+"collections");
        gitLnameToRepoPath.put("GR0013", normalPath+"einstances");
        gitLnameToRepoPath.put("GR0003", normalPath+"items");
        gitLnameToRepoPath.put("GR0014", normalPath+"iinstances");
        gitLnameToRepoPath.put("GR0006", normalPath+"persons");
        gitLnameToRepoPath.put("GR0005", normalPath+"places");
        gitLnameToRepoPath.put("GR0010", normalPath+"roles");
        gitLnameToRepoPath.put("GR0004", normalPath+"lineages");
        gitLnameToRepoPath.put("GR0001", normalPath+"corporations");
        gitLnameToRepoPath.put("GR0007", normalPath+"topics");
        prefixToGitLname.put("WAS", "GR0008");
        prefixToGitLname.put("ITW", "GR0003");
        prefixToGitLname.put("PRA", "GR0015");
        prefixToGitLname.put("WA", "GR0008");
        prefixToGitLname.put("MW", "GR0012");
        prefixToGitLname.put("PR", "GR0011");
        prefixToGitLname.put("IE", "GR0013");
        prefixToGitLname.put("IT", "GR0003");
        prefixToGitLname.put("W", "GR0014");
        prefixToGitLname.put("P", "GR0006");
        prefixToGitLname.put("G", "GR0005");
        prefixToGitLname.put("R", "GR0010");
        prefixToGitLname.put("L", "GR0004");
        prefixToGitLname.put("C", "GR0001");
        prefixToGitLname.put("T", "GR0007");
    }
    
    public static GitInfo gitInfoForResource(final Resource r) throws IOException {
        final String rLname = r.getLocalName();
        // first we guess and check if the file exists
        final GitInfo guessedGitInfo = new GitInfo();
        guessedGitInfo.pathInRepo = Models.getMd5(rLname)+"/"+rLname+".trig";
        final String typePrefix = RIDController.getTypePrefix(r.getLocalName());
        final String repoLname = prefixToGitLname.get(typePrefix);
        guessedGitInfo.repoLname = repoLname;
        String guessedPath = gitLnameToRepoPath.get(repoLname)+"/"+guessedGitInfo.pathInRepo;
        if ((new File(guessedPath)).exists()) {
            guessedGitInfo.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
            log.info("found git file %s for resource %s", guessedPath, r);
            return guessedGitInfo;
        }
        // if not, we try to take the part before the first underscore
        final int underscore_idx = rLname.indexOf('_');
        if (underscore_idx > 0) {
            final String rLname_guess2 = rLname.substring(0, underscore_idx);
            guessedGitInfo.pathInRepo = Models.getMd5(rLname_guess2)+"/"+rLname_guess2+".trig";
            // prefix and thus repoLname don't change in that case
            guessedPath = gitLnameToRepoPath.get(repoLname)+"/"+guessedGitInfo.pathInRepo;
            if ((new File(guessedPath)).exists()) {
                guessedGitInfo.ds = Helpers.datasetFromTrig(GlobalHelpers.readFileContent(guessedPath));
                log.info("found git file %s for resource %s", guessedPath, r);
                return guessedGitInfo;
            }
        }
        // if not we fall back to fuseki and look at the adminData
        
        // TODO
        
        // if not, we just return the guess with a null dataset
        
        return guessedGitInfo;
    }
    
    public static String getRevFromLatestLogEntry(final Model newModel, final Resource r) {
        // TODO: read the log entries, etc.
        return "updating "+r.getLocalName();
    }
    
    public static Dataset createDatasetForNewResource(final Model m, final Resource r) {
        // create graph
        // add adm:graphId in admin data
        final String graphUri = Models.BDG+r.getLocalName();
        Dataset ds = DatasetFactory.create();
        m.add(m.createResource(Models.BDA+r.getLocalName()), m.createProperty(Models.ADM, "graphId"), m.createResource(graphUri));
        ds.addNamedModel(graphUri, m);
        return null;
    }
    
    public static boolean pushRelevant(final String repoPath) {
        // TODO: make sure we don't push every time
        return true;
    }
    
    // This saves the new model in git and returns a Fuseki-ready dataset
    public static synchronized GitInfo saveInGit(final Model newModel, final Resource r, final Resource shape)
            throws IOException, VersionConflictException {
        final GitInfo gi = gitInfoForResource(r);
        Dataset result = null;
        String graphUri;
        if (gi.ds == null) {
            // new resource
            result = createDatasetForNewResource(newModel, r);
            graphUri = Models.BDG+r.getLocalName();
        } else {
            result = gi.ds;
            final Resource graph = ModelUtils.getMainGraph(result);
            graphUri = graph.getURI();
            // next lines changes the result variable directly
            ModelUtils.mergeModel(result, graphUri, newModel, r, shape);            
        }
        final String repoPath = gitLnameToRepoPath.get(gi.repoLname);
        final String filePath = repoPath+"/"+gi.pathInRepo;
        final FileOutputStream fos = new FileOutputStream(filePath);
        datasetToOutputStream(result, fos);
        final RevCommit rev = GitHelpers.commitChanges(repoPath, getRevFromLatestLogEntry(newModel, r));
        try {
            GitHelpers.push(repoPath,
                    EditConfig.getProperty("gitRemoteBase"), EditConfig.getProperty("gitUser"),
                    EditConfig.getProperty("gitPass"), EditConfig.getProperty("gitLocalRoot"));
        } catch (GitAPIException e) {
            log.error("could not push ", repoPath, e);
        }
        gi.ds = result;
        gi.revId = rev.getName();
        return gi;
    }

    private static void datasetToOutputStream(Dataset ds, OutputStream out) throws IOException {
        new STriGWriter().write(out, ds.asDatasetGraph(), EditConfig.prefix.getPrefixMap(), null, Helpers.createWriterContext());
        out.close();
    }
    
}
