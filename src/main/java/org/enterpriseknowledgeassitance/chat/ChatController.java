package org.enterpriseknowledgeassitance.chat;

import jakarta.validation.Valid;
import java.security.Principal;
import org.enterpriseknowledgeassitance.api.ApiPaths;
import org.enterpriseknowledgeassitance.api.ChatQueryRequest;
import org.enterpriseknowledgeassitance.api.ChatQueryResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

    private final ChatService chat;

    ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping(ApiPaths.CHAT_QUERY)
    ChatQueryResponse query(Principal principal, @Valid @RequestBody ChatQueryRequest request) {
        return chat.query(principal.getName(), request);
    }
}
