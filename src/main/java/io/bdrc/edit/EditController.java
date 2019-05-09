package io.bdrc.edit;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Status;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import io.bdrc.auth.Access;
import io.bdrc.auth.model.User;
import io.bdrc.edit.service.GitService;
import io.bdrc.edit.service.PatchService;
import io.bdrc.edit.txn.BUDATransaction;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

@Controller
@RequestMapping("/")
public class EditController {

    public String getUser(HttpServletRequest req) {
        User prof = ((Access) req.getAttribute("access")).getUser();
        if (prof != null) {
            return prof.getName();
        } else {
            return "TestUser";
        }
    }

    /**
     * create endpoint
     * 
     * @throws IOException
     * @throws ServiceSequenceException
     */
    @RequestMapping(value = "/graph}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, method = RequestMethod.POST)
    public ResponseEntity<String> acceptPatch(HttpServletRequest req, HttpServletResponse response, @PathVariable("resType") String resType, @RequestBody MultiValueMap<String, String> map) throws IOException, ServiceSequenceException {
        String user = getUser(req);
        PatchService ps = new PatchService(req);
        BUDATransaction btx = new BUDATransaction(ps.getId(), user);
        btx.setStatus(Status.STATUS_PREPARING);
        // btx.enlistResource(new ValidationService(ps.getId(),
        // Types.SIMPLE_VALIDATION_SVC, map.getFirst("type"), ps), 0);
        btx.enlistResource(new GitService(ps.getId(), resType, ps), 1);
        btx.enlistResource(ps, 2);
        btx.setStatus(Status.STATUS_PREPARED);
        return null;
    }

}
