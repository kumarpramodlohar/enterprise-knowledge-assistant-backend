package org.enterpriseknowledgeassitance.chat;

import java.util.List;

public interface ExternalSearchService {
    List<ExternalSearchResult> search(String query);
}
