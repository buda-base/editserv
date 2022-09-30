package io.bdrc.edit.controllers;

import java.io.FileWriter;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.bdrc.edit.EditConfig;

@Controller
@RequestMapping("/")
public class ClientErrorLogger {
    
    public final static Logger log = LoggerFactory.getLogger(ClientErrorLogger.class.getName());
    
    public synchronized static void writeLog(final HttpServletRequest req, final String rawlog) {
    	final String fname = EditConfig.getProperty("clienterror.filename");
    	if (fname == null) {
    		log.error("can't write log, no property clienterror.filename");
    		return;
    	}
    	FileWriter fw = null;
    	try {
			fw = new FileWriter(fname, true);
			final String date = "["+Instant.now().toString()+"]";
			fw.write(date);
			final String ip = " -- "+req.getHeader("X-Real-IP")+" -- ";
			fw.write(ip);
			fw.write(rawlog);
			fw.write("\n\n");
			fw.close();
		} catch (IOException e) {
			log.error("can't write in {}", fname, e);
			return;
		} finally {
			if (fw != null)
				try {
					fw.close();
				} catch (IOException e) {
					log.error("can't close {}", fname, e);
				}
		}
    	 
    }
    
    @PostMapping(value = "/logClientException")
    public static ResponseEntity<String> logClientException(@RequestBody String raw,
            HttpServletRequest req, HttpServletResponse response) {
    	writeLog(req, raw);
        return ResponseEntity.status(200)
                .body("ok");
    }
}
