package io.bdrc.edit.controllers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.libraries.Models;

@Controller
@RequestMapping("/")
public class SyncNotificationController {
	
	public final static Logger log = LoggerFactory.getLogger(SyncNotificationController.class.getName());
	public final static ObjectMapper mapper = new ObjectMapper();
	
	// temporary
	static final Resource syncUser = ResourceFactory.createResource(Models.BDU+"U00016");
	
    public static final class ImageGroupSyncInfo {
        @JsonProperty("pages_total")
        public Integer pages_total;
    }
	
    @PostMapping(value = "/notifysync/{wqname}/{iqname}", consumes = { MediaType.APPLICATION_JSON_VALUE })
    public synchronized ResponseEntity<String> syncImageGroup(@RequestBody() String requestbody, 
            @PathVariable("wqname") String wqname, @PathVariable("iqname") String iqname, HttpServletRequest req, HttpServletResponse response) throws IOException, EditException, GitAPIException {
    	if (wqname == null || iqname == null || requestbody == null || !wqname.startsWith("bdr:") || !iqname.startsWith("bdr:"))
    		throw new EditException("can't understand notifysync arguments "+ wqname + ", " + iqname + " , " + requestbody);
    	final Resource w = ResourceFactory.createResource(Models.BDR + wqname.substring(4));
    	final Resource i = ResourceFactory.createResource(Models.BDR + iqname.substring(4));
    	final GitInfo gi = CommonsGit.gitInfoForResource(w, true);
    	final ImageGroupSyncInfo igsyncinfo; 
    	try {
    	    igsyncinfo = mapper.readValue(requestbody, ImageGroupSyncInfo.class);
        } catch (IOException e) {
            log.error("can't parse request body "+requestbody, e);
            throw new EditException("can't parse request body "+requestbody);
        }
    	if (gi.ds == null || gi.ds.isEmpty()) {
    		log.error("no graph could be found for {}", wqname);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"err\":\"No graph could be found for " + wqname + "\"}");
    	}
    	final Model m = ModelUtils.getMainModel(gi.ds);
    	ModelUtils.addSyncNotification(m, i, igsyncinfo.pages_total, syncUser);
    	CommonsGit.commitAndPush(gi, "sync notification");
    	if (!EditConfig.dryrunmodefuseki)
    		FusekiWriteHelpers.putDataset(gi);
    	log.info("handled sync notification for {}, {}, {}", wqname, iqname, igsyncinfo.pages_total);
    	return ResponseEntity.status(HttpStatus.OK)
                .body("{}"); 
    }
    
    public static final TypeReference<HashMap<String, HashMap<String,ImageGroupSyncInfo>>> SyncInfo = new TypeReference<HashMap<String, HashMap<String,ImageGroupSyncInfo>>>() {};
    
    @PostMapping(value = "/notifysync")
    public synchronized ResponseEntity<String> syncBatch(@RequestBody() String syncInfoStr, 
            @PathVariable("wqname") String wqname, @PathVariable("iqname") String iqname, HttpServletRequest req, HttpServletResponse response) throws IOException, EditException, GitAPIException {
        if (syncInfoStr == null)
            throw new EditException("can't parse request body");
        final HashMap<String, HashMap<String,ImageGroupSyncInfo>> syncInfo;
        try {
            syncInfo = mapper.readValue(syncInfoStr, SyncInfo);
        } catch (IOException e) {
            log.error("can't parse request body "+syncInfoStr);
            throw new EditException("can't parse request body "+syncInfoStr);
        }
        for (final Entry<String, HashMap<String,ImageGroupSyncInfo>> winfo : syncInfo.entrySet()) {
            final Resource w = ResourceFactory.createResource(Models.BDR + winfo.getKey().substring(4));
            final Map<String,ImageGroupSyncInfo> iinfolist = winfo.getValue();
            final GitInfo gi = CommonsGit.gitInfoForResource(w, true);
            if (gi.ds == null || gi.ds.isEmpty()) {
                log.error("no graph could be found for {}", wqname);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body("{\"err\":\"No graph could be found for " + wqname + "\"}");
            }
            final Model m = ModelUtils.getMainModel(gi.ds);
            ModelUtils.addSyncNotification(m, w, iinfolist, syncUser);
            CommonsGit.commitAndPush(gi, "sync notification");
            if (!EditConfig.dryrunmodefuseki)
                FusekiWriteHelpers.putDataset(gi);
            log.info("handled sync notification for {}", wqname);
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body("{}"); 
    }
}
