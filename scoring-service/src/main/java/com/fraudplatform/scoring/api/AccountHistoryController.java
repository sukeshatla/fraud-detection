package com.fraudplatform.scoring.api;

import com.fraudplatform.scoring.application.AccountHistoryService;
import com.fraudplatform.scoring.application.TransactionHistoryEntry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read API over scoring's own data, used by the analyst dashboard's alert detail view. */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountHistoryController {

    private final AccountHistoryService history;

    AccountHistoryController(AccountHistoryService history) {
        this.history = history;
    }

    @GetMapping("/{accountId}/transactions")
    List<TransactionHistoryEntry> recent(@PathVariable String accountId, @RequestParam(defaultValue = "20") int limit) {
        return history.recent(accountId, limit);
    }
}
