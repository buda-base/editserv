package io.bdrc.edit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.patch.GitTaskService;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.BUDATransactionManager;
import io.bdrc.edit.txn.exceptions.ServiceException;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

@Controller
@RequestMapping("/")
public class EditController {

    public String getUser(HttpServletRequest req) {
        // User prof = ((Access) req.getAttribute("access")).getUser();
        // if (prof != null) {
        // return prof.getName();
        // } else {
        return "marc";
        // return null;
        // }
    }

    /**
     * Returns a task for a given user
     * 
     */
    @RequestMapping(value = "/tasks/{taskId}", produces = "application/json", method = RequestMethod.GET)
    public ResponseEntity<String> getTask(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) {
        String res = null;
        try {
            String userId = getUser(req);
            if (userId == null) {
                throw new ServiceException("Cannot get the tasks : user is null");
            }
            res = GitTaskService.getTaskAsJson(taskId, userId);
        } catch (ServiceException e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    /**
     * Returns all tasks of a given user
     * 
     * @throws ServiceException
     * 
     */
    @RequestMapping(value = "/tasks", produces = "application/json", method = RequestMethod.GET)
    public ResponseEntity<String> getAllOngoingTask(HttpServletRequest req, HttpServletResponse response) {
        String userId = getUser(req);
        String res = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            ArrayList<String> tsk = GitTaskService.getAllOngoingTaskId(userId);
            HashMap<String, String> tasks = new HashMap<>();
            ArrayList<JsonNode> test = new ArrayList<>();
            for (String s : tsk) {
                tasks.put(s, GitTaskService.getTaskAsJson(s, userId));
                test.add(mapper.readTree(GitTaskService.getTaskAsJson(s, userId)));
            }
            res = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(test);
        } catch (IOException | ServiceException e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    /**
     * Store a user ongoing task
     * 
     * @throws IOException
     * @throws ServiceSequenceException
     */

    @RequestMapping(value = "/tasks", consumes = "application/json", produces = "application/json", method = RequestMethod.PUT)
    public ResponseEntity<String> putTask(HttpServletRequest req, HttpServletResponse response, @RequestBody String jsonTask) {
        String userId = getUser(req);
        Task t = null;
        try {
            if (userId == null) {
                throw new ServiceException("Cannot save the task : user is null");
            }
            t = Task.create(jsonTask);
            GitTaskService.saveTask(t);

            // res = GitTaskService.getTaskAsJson(taskId, userId);
        } catch (ServiceException | IOException | GitAPIException e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.NOT_FOUND);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Location", "tasks/" + t.getId());
        return new ResponseEntity<>(responseHeaders, HttpStatus.NO_CONTENT);
    }

    /**
     * Store a user ongoing task
     * 
     * @throws IOException
     * @throws ServiceSequenceException
     */
    /*
     * @RequestMapping(value = "/tasks/{taskId}", consumes =
     * MediaType.APPLICATION_FORM_URLENCODED_VALUE, method = RequestMethod.PUT)
     * public ResponseEntity<String> acceptTask(HttpServletRequest req,
     * HttpServletResponse response) throws IOException, ServiceException,
     * ServiceSequenceException { String userId = getUser(req); TaskService ps = new
     * TaskService(req, userId); // For testing only
     * ps.savePatch(EditConstants.PTC_FINAL); System.out.println("Patch Service >>"
     * + ps); BUDATransaction btx = new BUDATransaction(ps.getId(), userId);
     * btx.setStatus(Status.STATUS_PREPARING); // btx.enlistResource(new
     * ValidationService(ps.getId(), // Types.SIMPLE_VALIDATION_SVC,
     * map.getFirst("type"), ps), 0); btx.enlistResource(new GitService(ps.getId(),
     * "test", ps), 1); btx.enlistResource(ps, 0);
     * btx.setStatus(Types.STATUS_QUEUED); BUDATransactionManager.queueTxn(btx);
     * HttpHeaders responseHeaders = new HttpHeaders();
     * responseHeaders.set("Location", "queuejob/" + ps.getId()); return new
     * ResponseEntity<>("ok", responseHeaders, HttpStatus.ACCEPTED); }
     */

    @RequestMapping(value = "/queuejob/{id}", method = RequestMethod.GET)
    public ResponseEntity<String> getTxnInfo(HttpServletRequest req, HttpServletResponse response, @PathVariable("id") String id) {
        String status = BUDATransactionManager.getTxnStatus(id);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    public String getJsonErrorString(Exception e) {
        return "{ \"exception\": \"" + e.getClass().getCanonicalName() + "\",\n" + "    \"error\": \"" + e.getMessage() + "\"}";
    }

}
