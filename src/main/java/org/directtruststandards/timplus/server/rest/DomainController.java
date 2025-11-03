package org.directtruststandards.timplus.server.rest;

import org.directtruststandards.timplus.server.rest.dto.DomainCreationRequest;
import org.directtruststandards.timplus.server.rest.dto.DomainCreationResponse;
import org.jivesoftware.openfire.domain.DomainManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * REST controller for domain management operations
 */
@RestController
@RequestMapping("/api/domains")
public class DomainController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainController.class);
    
    @PostMapping("/create")
    public ResponseEntity<DomainCreationResponse> createDomain(@Valid @RequestBody DomainCreationRequest request) {
        try {
            String domainName = request.getDomainName();
            
            // Check if domain already exists
            if (DomainManager.getInstance().isRegisteredDomain(domainName)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new DomainCreationResponse(false, "Domain already exists", domainName));
            }
            
            // Create the domain
            DomainManager.getInstance().createDomain(domainName, true);
            
            LOGGER.info("Domain '{}' created via REST API", domainName);
            
            return ResponseEntity.ok(new DomainCreationResponse(true, "Domain created successfully", domainName));
            
        } catch (Exception e) {
            LOGGER.error("Failed to create domain: " + request.getDomainName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new DomainCreationResponse(false, "Internal server error", request.getDomainName()));
        }
    }
    
    @GetMapping("/{domainName}/exists")
    public ResponseEntity<Boolean> domainExists(@PathVariable String domainName) {
        boolean exists = DomainManager.getInstance().isRegisteredDomain(domainName);
        return ResponseEntity.ok(exists);
    }
}