package io.bdrc.edit.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.jena.graph.Node;
import org.seaborne.patch.PatchHeader;

public class EditPatchHeaders {

    public static final String KEY_GRAPH = "graph";
    public static final String KEY_MAPPING = "mapping";
    public static final String KEY_CREATE = "create";
    public static final String KEY_DELETE = "delete";
    public static final String KEY_ID = "id";

    private PatchHeader ph;
    private HashMap<String, String> mappings;

    public EditPatchHeaders(PatchHeader header) {
        this.ph = header;
        mappings = getResTypeMapping();
    }

    public List<String> getGraphUris() {
        List<String> gph = new ArrayList<>();
        Node graphs = ph.get(KEY_GRAPH);
        if (graphs != null) {
            gph = Arrays.asList(graphs.getLiteral().toString().split(","));
        }
        return gph;
    }

    public List<String> getCreateUris() {
        List<String> gph = new ArrayList<>();
        Node graphs = ph.get(KEY_CREATE);
        if (graphs != null) {
            gph = Arrays.asList(graphs.getLiteral().toString().split(","));
        }
        return gph;
    }

    public List<String> getDeleteUris() {
        List<String> dph = new ArrayList<>();
        Node graphs = ph.get(KEY_DELETE);
        if (graphs != null) {
            dph = Arrays.asList(graphs.getLiteral().toString().split(","));
        }
        return dph;
    }

    public String getResourceType(String resId) {
        return mappings.get(resId);
    }

    public HashMap<String, String> getResTypeMapping() {
        HashMap<String, String> resTypes = new HashMap<>();
        List<String> map = new ArrayList<>();
        Node restype = ph.get(KEY_MAPPING);
        if (restype != null) {
            map = Arrays.asList(restype.getLiteral().toString().split(","));
        }
        for (String res : map) {
            String[] parts = res.split("-");
            resTypes.put(parts[0], parts[1]);
        }
        return resTypes;
    }

    public String getPatchId() {
        String tmp = ph.get(KEY_ID).toString();
        return tmp.substring(tmp.indexOf(":") + 1);
    }

}
