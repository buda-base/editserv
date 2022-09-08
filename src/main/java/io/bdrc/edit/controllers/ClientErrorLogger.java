package io.bdrc.edit.controllers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class ClientErrorLogger {
    
    public final static Logger log = LoggerFactory.getLogger(ClientErrorLogger.class.getName());
    
    @PostMapping(value = "/logClientException")
    public static ResponseEntity<String> logClientException(@RequestBody String raw,
            HttpServletRequest req, HttpServletResponse response) {
        log.error(raw);
        return ResponseEntity.status(200)
                .body("ok");
    }
}
