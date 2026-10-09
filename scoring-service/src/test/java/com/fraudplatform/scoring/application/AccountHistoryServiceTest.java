package com.fraudplatform.scoring.application;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountHistoryServiceTest {

    @Mock
    private TransactionHistoryQuery query;

    @ParameterizedTest(name = "requested {0} → queried {1}")
    @CsvSource({"20,20", "0,1", "-5,1", "500,100"})
    @DisplayName("Limit is clamped to 1..100 so a client can't request an unbounded scan")
    void clampsLimit(int requested, int expected) {
        new AccountHistoryService(query).recent("acc-1", requested);

        verify(query).recentForAccount("acc-1", expected);
    }
}
