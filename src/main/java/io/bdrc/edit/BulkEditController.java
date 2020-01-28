package io.bdrc.edit;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.helpers.BulkOps;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.edit.txn.exceptions.ModuleException;

@Controller
@RequestMapping("/")
public class BulkEditController {

	public final static Logger log = LoggerFactory.getLogger(BulkEditController.class.getName());

	/**
	 * Replace references to res A by references to ref to B Mark res A with the
	 * Withdrawn status.
	 * 
	 * Request params:
	 * 
	 * - withdrawn: the full uri of the resource to replace and mark as withdrawn -
	 * - valid: the full uri of the replacement resource
	 * 
	 * @throws IOException
	 * @throws GitAPIException
	 * @throws TransportException
	 * @throws InvalidRemoteException
	 * @throws NoSuchAlgorithmException
	 * @throws DataUpdateException
	 * 
	 * @throws ModuleException
	 */
	@PostMapping(value = "/withdrawn")
	public ResponseEntity<String> withdrawn(HttpServletRequest req, HttpServletResponse response, @RequestBody String json) {
		try {
			JsonNode node = new ObjectMapper().readTree(json);
			String withdrawn = node.findValue("withdrawn").asText();
			String valid = node.findValue("valid").asText();
			String fusekiUrl = node.findValue("fusekiUrl").asText();
			BulkOps.replaceAllDuplicateByValid(withdrawn, valid, fusekiUrl);
		} catch (Exception e) {
			log.error("A error occured while processing withdraw operation for " + json);
			return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return new ResponseEntity<>(req.getRemoteAddr(), HttpStatus.OK);
	}

	public String getJsonErrorString(Exception e) {
		return "{ \"exception\": \"" + e.getClass().getCanonicalName() + "\",\n" + "    \"error\": \"" + e.getMessage() + "\"}";
	}
}
