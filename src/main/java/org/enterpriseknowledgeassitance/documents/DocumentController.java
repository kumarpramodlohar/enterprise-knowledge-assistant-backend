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

@RestController
@RequestMapping(ApiPaths.DOCUMENTS)
class DocumentController {

    private final UploadService uploads;

    DocumentController(UploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    UploadAcceptedResponse upload(Principal principal, @RequestParam("collectionId") UUID collectionId,
            @RequestParam("file") MultipartFile file, @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "tags", required = false) List<String> tags) {
        return uploads.accept(principal.getName(), collectionId, file, version, documentType, tags);
    }
}
