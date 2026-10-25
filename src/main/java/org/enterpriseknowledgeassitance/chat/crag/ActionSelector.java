package org.enterpriseknowledgeassitance.chat.crag;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ActionSelector {
    public enum Action { CORRECT, AMBIGUOUS, INCORRECT }

    public Action select(List<CragEvaluation> evaluations) {
        if (evaluations.stream().anyMatch(e -> e.verdict() == CragEvaluation.Verdict.CORRECT)) {
            return Action.CORRECT;
        }
        if (evaluations.stream().anyMatch(e -> e.verdict() == CragEvaluation.Verdict.AMBIGUOUS)) {
            return Action.AMBIGUOUS;
        }
        return Action.INCORRECT;
    }
}
