package org.enterpriseknowledgeassitance.documents;

import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.security.AuthServiceProperties;
import org.enterpriseknowledgeassitance.security.IdentityAssertion;
import org.springframework.security.core.Authentication;
import org.enterpriseknowledgeassitance.api.UploadAcceptedResponse;
import org.enterpriseknowledgeassitance.api.ApiPaths;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.enterpriseknowledgeassitance.api.DocumentListQuery;
import org.enterpriseknowledgeassitance.api.DocumentPage;
import org.enterpriseknowledgeassitance.api.DocumentResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping(ApiPaths.DOCUMENTS)
class DocumentController {

    private final UploadService uploads;
    private final DocumentService documents;
    private final AuthServiceProperties authServiceProperties;

    DocumentController(UploadService uploads, DocumentService documents, AuthServiceProperties authServiceProperties) {
        this.uploads = uploads;
        this.documents = documents;
        this.authServiceProperties = authServiceProperties;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    UploadAcceptedResponse upload(Authentication authentication, @RequestParam("collectionId") UUID collectionId,
            @RequestParam("file") MultipartFile file, @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "tags", required = false) List<String> tags) {
        return authServiceProperties.enabled()
                ? uploads.accept(identity(authentication), collectionId, file, version, documentType, tags)
                : uploads.accept(authentication.getName(), collectionId, file, version, documentType, tags);
    }

    @GetMapping
    DocumentPage list(Authentication authentication, @Valid DocumentListQuery query) {
        return authServiceProperties.enabled() ? documents.list(identity(authentication), query)
                : documents.list(authentication.getName(), query);
    }

    @GetMapping("/{id}")
    DocumentResponse get(Authentication authentication, @PathVariable UUID id) {
        return authServiceProperties.enabled() ? documents.get(identity(authentication), id)
                : documents.get(authentication.getName(), id);
    }

    @PostMapping("/{id}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DocumentResponse reindex(Authentication authentication, @PathVariable UUID id) {
        return authServiceProperties.enabled() ? documents.reindex(identity(authentication), id)
                : documents.reindex(authentication.getName(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable UUID id) {
        if (authServiceProperties.enabled()) {
            documents.delete(identity(authentication), id);
        } else {
            documents.delete(authentication.getName(), id);
        }
    }

    private IdentityAssertion identity(Authentication authentication) {
        return IdentityAssertion.from(authentication, authServiceProperties.localNamespace());
    }
}
