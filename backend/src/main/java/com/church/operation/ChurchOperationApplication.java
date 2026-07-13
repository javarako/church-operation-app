package com.church.operation;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.config.FiscalYearProperties;
import com.church.operation.config.PasswordResetProperties;
import com.church.operation.config.MemberImageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    ChurchInformationProperties.class,
    FiscalYearProperties.class,
    PasswordResetProperties.class,
    MemberImageProperties.class
})
public class ChurchOperationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChurchOperationApplication.class, args);
    }
}
