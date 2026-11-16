package sg.hawkscc.platform.finance.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.finance.FinanceService;
import sg.hawkscc.platform.finance.FinanceViews;
import sg.hawkscc.platform.identity.CurrentMember;

/** A member's own money: what they owe, item by item, and their statement. */
@RestController
class MyFinanceController {

    private final FinanceService finance;

    MyFinanceController(FinanceService finance) {
        this.finance = finance;
    }

    @GetMapping("/api/me/statement")
    FinanceViews.Statement statement(@AuthenticationPrincipal CurrentMember me) {
        return finance.myStatement(me);
    }
}
