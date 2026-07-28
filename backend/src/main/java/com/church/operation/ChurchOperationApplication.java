package com.church.operation;

import com.church.operation.config.ChurchInformationProperties;
import com.church.operation.config.ChurchTimeZoneDefaultsProperties;
import com.church.operation.config.DataManagementProperties;
import com.church.operation.config.FiscalYearDefaultsProperties;
import com.church.operation.config.PasswordResetProperties;
import com.church.operation.config.MemberImageProperties;
import com.church.operation.config.RuntimeEmailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    ChurchInformationProperties.class,
    ChurchTimeZoneDefaultsProperties.class,
    DataManagementProperties.class,
    FiscalYearDefaultsProperties.class,
    PasswordResetProperties.class,
    MemberImageProperties.class,
    RuntimeEmailProperties.class
})
public class ChurchOperationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChurchOperationApplication.class, args);
    }
}
