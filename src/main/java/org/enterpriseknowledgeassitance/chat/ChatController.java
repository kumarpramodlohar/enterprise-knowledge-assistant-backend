package org.enterpriseknowledgeassitance.chat;

import jakarta.validation.Valid;
import org.enterpriseknowledgeassitance.api.ApiPaths;
import org.enterpriseknowledgeassitance.api.ChatQueryRequest;
import org.enterpriseknowledgeassitance.api.ChatQueryResponse;
import org.enterpriseknowledgeassitance.security.AuthServiceProperties;
import org.enterpriseknowledgeassitance.security.IdentityAssertion;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

    private final ChatService chat;
    private final AuthServiceProperties authServiceProperties;

    ChatController(ChatService chat, AuthServiceProperties authServiceProperties) {
        this.chat = chat;
        this.authServiceProperties = authServiceProperties;
    }

    @PostMapping(ApiPaths.CHAT_QUERY)
    ChatQueryResponse query(Authentication authentication, @Valid @RequestBody ChatQueryRequest request) {
        return authServiceProperties.enabled()
                ? chat.query(IdentityAssertion.from(authentication, authServiceProperties.localNamespace()), request)
                : chat.query(authentication.getName(), request);
    }
}
