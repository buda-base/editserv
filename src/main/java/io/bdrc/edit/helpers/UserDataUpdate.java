package io.bdrc.edit.helpers;

import java.util.HashMap;
import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.libraries.GitHelpers;

public class UserDataUpdate {

    public final static Logger log = LoggerFactory.getLogger(UserDataUpdate.class.getName());

    PatchContent pc;
    private List<String> graphs;
    private DatasetGraph dsg;
    private HashMap<String, AdminData> admData;
    private HashMap<String, String> gitRev;
    private String userId;

    public UserDataUpdate(PatchContent pc, String editor, String userId) throws DataUpdateException {
        this.pc = pc;
        this.userId = userId;
        this.graphs = pc.getEditPatchHeaders().getGraphUris();
        prepareModels();
    }

    private void prepareModels() throws DataUpdateException {
        Dataset ds = DatasetFactory.create();
        dsg = ds.asDatasetGraph();
        admData = new HashMap<>();
        // Fetching the graphs, building the dataset to be patched
        for (String st : graphs) {
            Node graphUri = NodeFactory.createURI(st);
            try {
                log.info("graphUri {} and patchheaders {}", graphUri.getURI(), pc.getEditPatchHeaders().getResTypeMapping());
                AdminData ad = Helpers.fetchAdminInfo(graphUri.getURI(), pc.getEditPatchHeaders());
                log.info("Admin Data {} ", ad);
                admData.put(graphUri.getURI(), ad);
                String repoName = EditConfig.getProperty("usersGitLocalRoot");
                Model m = ModelFactory.createModelForGraph(Helpers.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(repoName, ad.getGitPath())));
                dsg.addGraph(graphUri, m.getGraph());
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new DataUpdateException("No graph could be fetched for " + st);
            }
        }
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getGraphs() {
        return graphs;
    }

    public DatasetGraph getDatasetGraph() {
        return dsg;
    }

    public String getPatch() {
        return pc.getContent();
    }

}
