package io.bdrc.edit.controllers;

import java.io.IOException;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
	
	// temporary
	static final Resource syncUser = ResourceFactory.createResource(Models.BDU+"U00016");
	
    @PostMapping(value = "/notifysync/{wqname}/{iqname}")
    public synchronized ResponseEntity<String> getLatestID(@RequestParam("pagestotal") Integer pagestotal, 
            @PathVariable("wqname") String wqname, @PathVariable("iqname") String iqname, HttpServletRequest req, HttpServletResponse response) throws IOException, EditException, GitAPIException {
    	if (wqname == null || iqname == null || pagestotal == null || !wqname.startsWith("bdr:") || !iqname.startsWith("bdr:"))
    		throw new EditException("can't understand notifysync arguments "+ wqname + ", " + iqname + " , " + pagestotal);
    	final Resource w = ResourceFactory.createResource(Models.BDR + wqname.substring(4));
    	final Resource i = ResourceFactory.createResource(Models.BDR + iqname.substring(4));
    	final GitInfo gi = CommonsGit.gitInfoForResource(w, true);
    	if (gi.ds == null || gi.ds.isEmpty()) {
    		log.error("no graph could be found for {}", wqname);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body("No graph could be found for " + wqname);
    	}
    	final Model m = ModelUtils.getMainModel(gi.ds);
    	ModelUtils.addSyncNotification(m, i, pagestotal, syncUser);
    	CommonsGit.commitAndPush(gi, "sync notification");
    	if (!EditConfig.dryrunmodefuseki)
    		FusekiWriteHelpers.putDataset(gi);
    	log.info("handled sync notification for {}, {}, {}", wqname, iqname, pagestotal);
    	return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(null); 
    }
}
