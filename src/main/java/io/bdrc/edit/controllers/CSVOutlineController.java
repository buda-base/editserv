package io.bdrc.edit.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.opencsv.CSVWriter;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.libraries.StreamingHelpers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


@Controller
@RequestMapping("/")
class CSVOutlineController {

    @GetMapping(value = "/outline/csv/{qname}")
    public void downloadCSV(@PathVariable("qname") String qname, final HttpServletRequest req, HttpServletResponse response) throws IOException {
        // Set the content type and attachment header.
        response.setContentType("text/csv");
        final String lname = qname.substring(4);
        response.setHeader("Content-Disposition", "attachment; filename="+lname+".csv");
        
        if (EditConfig.useAuth) {
            final AccessInfo acc = (AccessInfo) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
                isAdmin = acc.isAdmin();
            } catch (EditException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(StreamingHelpers.getStream(e.getMessage()));
            }
        }
        try {
            CommonsGit.GitInfo gi = CommonsGit.gitInfoForResource(res, false);
        
        // Create CSV writer for writing CSV content
        try (CSVWriter csvWriter = new CSVWriter(response.getWriter())) {
            // Define the header
            String[] header = {"Column1", "Column2"};
            csvWriter.writeNext(header);
            
            // Write example records
            List<String[]> data = Arrays.asList(
                    new String[]{"Example1", "Example2"},
                    new String[]{"Example3", "Example4"}
            );
            csvWriter.writeAll(data);
        }
    }
}
