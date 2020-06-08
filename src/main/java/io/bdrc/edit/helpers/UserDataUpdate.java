package io.bdrc.edit.helpers;

import java.util.HashMap;
import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.GitHelpers;

public class UserDataUpdate {

    public final static Logger log = LoggerFactory.getLogger(UserDataUpdate.class.getName());

    PatchContent pc;
    private List<String> graphs;
    private DatasetGraph dsg;
    private HashMap<String, AdminData> admData;
    private String gitRev;
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
        String s = null;
        try {
            // update local gitrepo
            Helpers.pullOrCloneUsers();
            // Fetching the graphs, building the dataset to be patched
            for (String st : graphs) {
                s = st;
                Node graphUri = NodeFactory.createURI(st);
                log.info("graphUri {} and patchheaders {}", graphUri.getURI(), pc.getEditPatchHeaders().getResTypeMapping());
                AdminData ad = Helpers.fetchAdminInfo(graphUri.getURI(), pc.getEditPatchHeaders());
                log.info("Admin Data {} ", ad);
                admData.put(graphUri.getURI(), ad);
                String repoName = EditConfig.getProperty("usersGitLocalRoot");
                dsg = Helpers.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(repoName, ad.getGitPath()));
                dsg.addGraph(graphUri, dsg.getGraph(NodeFactory.createURI(BudaUser.PUBLIC_PFX + userId)));
                dsg.addGraph(NodeFactory.createURI(st.replace("/user/", "/user-private/")),
                        dsg.getGraph(NodeFactory.createURI(BudaUser.PRIVATE_PFX + userId)));

            }
        } catch (Exception ex) {
            log.error("Prepare models failed in UserDataUpdate ", ex);
            throw new DataUpdateException("No graph could be fetched for " + s);
        }
    }

    public void setGitRevisionInfo(String sha1) {
        this.gitRev = sha1;
    }

    public String getGitRevisionInfo() {
        return gitRev;
    }

    public EditPatchHeaders getEditPatchHeaders() {
        return pc.getEditPatchHeaders();
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
