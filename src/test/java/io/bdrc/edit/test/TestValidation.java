package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.MainEditController;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { MainEditController.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class TestValidation {

    String validModel = "P707.ttl";
    String nameErrModel = "P707_nameErrs.ttl";

    @Autowired
    Environment environment;

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
    }

    @Test
    public void validateAndSave() throws IOException {
        InputStream in = TestValidation.class.getClassLoader().getResourceAsStream(validModel);
        StringWriter writer = new StringWriter();
        IOUtils.copy(in, writer, "UTF-8");
        String model = writer.toString();
        // System.out.println("Read Model to validate >>" + model);
        HttpClient client = HttpClientBuilder.create().build();
        HttpPut put = new HttpPut("http://localhost:" + environment.getProperty("local.server.port") + "/putresource/bdr:P707");
        StringEntity entity = new StringEntity(model);
        put.setEntity(entity);
        put.setHeader("Content-type", "text/turtle");
        HttpResponse response = client.execute(put);
        System.out.println(response);
    }

}
