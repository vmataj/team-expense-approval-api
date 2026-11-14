package com.vmataj.expense;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LedgerPage {
    @GetMapping("/")
    String dashboard() {
        return "forward:/ledger.html";
    }

    @GetMapping("/approvals")
    String approvals() {
        return "forward:/index.html";
    }
}
