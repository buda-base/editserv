package io.bdrc.edit;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
     */
    @RequestMapping(value = "/graph}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, method = RequestMethod.POST)
    public ResponseEntity<String> acceptPatch(HttpServletRequest req, HttpServletResponse response, @PathVariable("resType") String resType, @RequestBody MultiValueMap<String, String> map) throws IOException {
        String user = getUser(req);
        return null;
    }

}
