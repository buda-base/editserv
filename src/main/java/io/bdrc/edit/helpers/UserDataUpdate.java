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

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.libraries.GitHelpers;

public class UserDataUpdate {

    PatchContent pc;
    private List<String> graphs;
    private DatasetGraph dsg;
    private HashMap<String, AdminData> admData;
    private HashMap<String, String> gitRev;

    public UserDataUpdate(PatchContent pc, String editor, String userId) throws DataUpdateException {
        this.pc = pc;
        this.graphs = pc.getEditPatchHeaders().getGraphUris();
        prepareModels();
    }

    private void prepareModels() throws DataUpdateException {
        Dataset ds = DatasetFactory.create();
        dsg = ds.asDatasetGraph();

        // Fetching the graphs, building the dataset to be patched
        for (String st : graphs) {
            Node graphUri = NodeFactory.createURI(st);
            try {
                AdminData ad = Helpers.fetchAdminInfo(graphUri.getURI(), pc.getEditPatchHeaders());
                admData.put(graphUri.getURI(), ad);
                String repoName = EditConfig.getProperty("usersGitLocalRoot") + "users";
                Model m = ModelFactory.createModelForGraph(Helpers.buildGraphFromTrig(GitHelpers.getGitHeadFileContent(repoName, ad.getGitPath())));
                dsg.addGraph(graphUri, m.getGraph());
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new DataUpdateException("No graph could be fetched for " + st);
            }
        }
    }

}
