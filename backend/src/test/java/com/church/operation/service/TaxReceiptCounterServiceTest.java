package com.church.operation.service;

import com.church.operation.entity.TaxReceiptCounter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxReceiptCounterServiceTest {
    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void allocatesSixDigitSequenceWithinTaxYear() {
        TaxReceiptCounter first = counter(2026, 1);
        TaxReceiptCounter second = counter(2026, 2);
        when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(TaxReceiptCounter.class)
        )).thenReturn(first, second);
        TaxReceiptCounterService service = new TaxReceiptCounterService(mongoTemplate);

        assertThat(service.nextReceiptNumber(2026)).isEqualTo("2026-000001");
        assertThat(service.nextReceiptNumber(2026)).isEqualTo("2026-000002");
    }

    private TaxReceiptCounter counter(int year, long sequence) {
        TaxReceiptCounter counter = new TaxReceiptCounter();
        counter.setTaxYear(year);
        counter.setNextSequence(sequence);
        return counter;
    }
}
