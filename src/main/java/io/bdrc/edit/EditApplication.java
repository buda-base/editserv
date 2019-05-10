package io.bdrc.edit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.txn.BUDATransactionManager;

@SpringBootApplication
@Configuration
@EnableAutoConfiguration
@Primary
@ComponentScan(basePackages = { "io.bdrc.edit" })

public class EditApplication extends SpringBootServletInitializer {

    public static void main(String[] args) throws Exception {
        EditConfig.init();
        RdfAuthModel.init();
        SpringApplication.run(EditApplication.class, args);
        Thread t = new Thread(BUDATransactionManager.getInstance());
        t.start();
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(EditApplication.class);
    }

}