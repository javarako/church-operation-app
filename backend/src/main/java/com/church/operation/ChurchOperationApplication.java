package com.church.operation;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.config.FiscalYearProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ChurchInformationProperties.class, FiscalYearProperties.class})
public class ChurchOperationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChurchOperationApplication.class, args);
    }
}
