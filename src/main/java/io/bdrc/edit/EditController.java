package io.bdrc.edit;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Status;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import io.bdrc.auth.Access;
import io.bdrc.auth.model.User;
import io.bdrc.edit.service.GitService;
import io.bdrc.edit.service.PatchService;
import io.bdrc.edit.txn.BUDATransaction;
import io.bdrc.edit.txn.BUDATransactionManager;
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
    @RequestMapping(value = "/graph", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, method = RequestMethod.POST)
    public ResponseEntity<String> acceptPatch(HttpServletRequest req, HttpServletResponse response) throws IOException, ServiceSequenceException {
        String user = getUser(req);
        PatchService ps = new PatchService(req);
        System.out.println("Patch Service >>" + ps);
        BUDATransaction btx = new BUDATransaction(ps.getId(), user);
        btx.setStatus(Status.STATUS_PREPARING);
        // btx.enlistResource(new ValidationService(ps.getId(),
        // Types.SIMPLE_VALIDATION_SVC, map.getFirst("type"), ps), 0);
        btx.enlistResource(new GitService(ps.getId(), "test", ps), 1);
        btx.enlistResource(ps, 0);
        btx.setStatus(Types.STATUS_QUEUED);
        BUDATransactionManager.queueTxn(btx);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Location", "queuejob/" + ps.getId());
        return new ResponseEntity<>("ok", responseHeaders, HttpStatus.ACCEPTED);
    }

    @RequestMapping(value = "/queuejob/{id}", method = RequestMethod.GET)
    public ResponseEntity<String> getTxnInfo(HttpServletRequest req, HttpServletResponse response, @PathVariable("id") String id) {
        String status = BUDATransactionManager.getTxnStatus(id);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

}
