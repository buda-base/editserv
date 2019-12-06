package io.bdrc.edit.helpers;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.core.DatasetGraph;
import org.seaborne.patch.text.RDFPatchReaderText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.libraries.GitHelpers;
import io.bdrc.libraries.GlobalHelpers;

public class DataUpdate {

    private Task tsk;
    private EditPatchHeaders ph;
    private List<String> create;
    private List<String> graphs;
    private List<String> delete;
    private List<String> replace;
    private DatasetGraph dsg;
    private HashMap<String, AdminData> admData;
    private HashMap<String, String> gitRev;

    public final static Logger log = LoggerFactory.getLogger(DataUpdate.class.getName());

    public DataUpdate(Task tsk) throws DataUpdateException, NoSuchAlgorithmException, UnsupportedEncodingException {
        super();
        this.tsk = tsk;
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(tsk.getPatch().getBytes())));
        this.create = ph.getCreateUris();
        this.graphs = ph.getGraphUris();
        this.delete = ph.getDeleteUris();
        this.replace = ph.getReplaceUrisPairs();
        this.admData = new HashMap<>();
        this.gitRev = new HashMap<>();
        prepareModels();
    }

    private void prepareModels() throws DataUpdateException, NoSuchAlgorithmException, UnsupportedEncodingException {

        Dataset ds = DatasetFactory.create();
        dsg = ds.asDatasetGraph();

        // Fetching the graphs, building the dataset to be patched
        for (String st : graphs) {
            Node graphUri = NodeFactory.createURI(st);
            try {
                AdminData ad = Helpers.fetchAdminInfo(graphUri.getURI(), ph);
                admData.put(graphUri.getURI(), ad);
                String repoName = EditConfig.getProperty("gitLocalRoot") + ad.getGitRepo().getGitRepoName();
                Model m = ModelFactory.createModelForGraph(Helpers.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(repoName, ad.getGitPath())));
                dsg.addGraph(graphUri, m.getGraph());
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new DataUpdateException("No graph could be fetched as " + st + " for patchId:" + ph.getPatchId());
            }
        }

        for (String rep : replace) {
            Node graphUri = NodeFactory.createURI(rep);
            try {
                AdminData ad = Helpers.fetchAdminInfo(graphUri.getURI(), ph);
                admData.put(graphUri.getURI(), ad);
                String repoName = EditConfig.getProperty("gitLocalRoot") + ad.getGitRepo().getGitRepoName();
                Model m = ModelFactory.createModelForGraph(Helpers.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(repoName, ad.getGitPath())));
                dsg.addGraph(graphUri, m.getGraph());
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new DataUpdateException("No graph could be fetched as " + rep + " for patchId:" + ph.getPatchId());
            }
        }
        // Listing the graphs to create
        List<String> create = ph.getCreateUris();
        // add empty named graphs to the dataset
        for (String c : create) {
            Node graphUri = NodeFactory.createURI(c);
            dsg.addGraph(graphUri, Graph.emptyGraph);
            Graph g = dsg.getGraph(graphUri);
            Model m = ModelFactory.createModelForGraph(g);
            removeGitRevisionInfo(c, m);
            createAdminInfo(graphUri.getURI());
            dsg.addGraph(graphUri, m.getGraph());
        }

        // Listing the graphs to delete
        List<String> delete = ph.getDeleteUris();
        // add empty named graphs to the dataset
        for (String d : delete) {
            Node graphUri = NodeFactory.createURI(d);
            dsg.addGraph(graphUri, Graph.emptyGraph);
            Graph g = dsg.getGraph(graphUri);
            Model m = ModelFactory.createModelForGraph(g);
            removeGitRevisionInfo(d, m);
            createAdminInfo(graphUri.getURI());
            dsg.addGraph(graphUri, m.getGraph());
        }
    }

    private Model removeGitRevisionInfo(String graphUri, Model m) {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        Triple tpl = Triple.create(ResourceFactory.createResource(EditConstants.BDA + resId).asNode(), EditConstants.GIT_REVISION.asNode(), Node.ANY);
        Graph g = m.getGraph();
        List<Triple> list = g.find(tpl).toList();
        for (Triple t : list) {
            g.delete(t);
        }
        return ModelFactory.createModelForGraph(g);
    }

    public void addGitRevisionInfo(String graph, String sha1) {
        gitRev.put(graph, sha1);
    }

    public List<String> getAllAffectedGraphs() {
        List<String> all = Stream.of(graphs, create).flatMap(x -> x.stream()).collect(Collectors.toList());
        return all;
    }

    private AdminData createAdminInfo(String graphUri) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        log.info("ADMIN INFO for URI : {}", graphUri);
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData ad = new AdminData(resId, Helpers.getResourceType(graphUri, ph), getGitDir(resId));
        admData.put(graphUri, ad);
        return ad;
    }

    private String getGitDir(String resId) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        int index = resId.indexOf('_');
        if (index != -1) {
            resId = resId.substring(0, index);
        }
        return GlobalHelpers.getTwoLettersBucket(resId);
    }

    public static String buildReplacePatch(String uriToReplace, String newUri) {
        StringBuffer sb = new StringBuffer();
        sb.append("TX .");
        Model m = QueryProcessor.getTriplesWithObject(uriToReplace, null);
        StmtIterator it = m.listStatements();
        while (it.hasNext()) {
            Statement st = it.next();
            String delCmd = "D <" + st.getSubject().getURI() + "> <" + st.getPredicate().getURI() + "> <" + uriToReplace + "> <" + st.getSubject().getURI() + ">";
            String addCmd = "A <" + st.getSubject().getURI() + "> <" + st.getPredicate().getURI() + "> <" + newUri + "> <" + st.getSubject().getURI() + ">";
            sb.append(System.lineSeparator() + delCmd);
            sb.append(System.lineSeparator() + addCmd);
        }
        sb.append("TC .");
        return sb.toString();
    }

    public AdminData getAdminData(String graphUri) {
        return admData.get(graphUri);
    }

    public List<String> getCreate() {
        return create;
    }

    public List<String> getDelete() {
        return delete;
    }

    public List<String> getReplace() {
        return replace;
    }

    public List<String> getGraphs() {
        return graphs;
    }

    public String getUserId() {
        return tsk.getUser();
    }

    public String getTaskId() {
        return tsk.getId();
    }

    public String getPatch() {
        return tsk.getPatch();
    }

    public HashMap<String, String> getGitRev() {
        return gitRev;
    }

    public EditPatchHeaders getEditPatchHeaders() {
        return ph;
    }

    public DatasetGraph getDatasetGraph() {
        return dsg;
    }
}
