package org.enterpriseknowledgeassitance.documents;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
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

    DocumentController(UploadService uploads, DocumentService documents) {
        this.uploads = uploads;
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    UploadAcceptedResponse upload(Principal principal, @RequestParam("collectionId") UUID collectionId,
            @RequestParam("file") MultipartFile file, @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "tags", required = false) List<String> tags) {
        return uploads.accept(principal.getName(), collectionId, file, version, documentType, tags);
    }

    @GetMapping
    DocumentPage list(Principal principal, @Valid DocumentListQuery query) {
        return documents.list(principal.getName(), query);
    }

    @GetMapping("/{id}")
    DocumentResponse get(Principal principal, @PathVariable UUID id) {
        return documents.get(principal.getName(), id);
    }

    @PostMapping("/{id}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DocumentResponse reindex(Principal principal, @PathVariable UUID id) {
        return documents.reindex(principal.getName(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Principal principal, @PathVariable UUID id) {
        documents.delete(principal.getName(), id);
    }
}
