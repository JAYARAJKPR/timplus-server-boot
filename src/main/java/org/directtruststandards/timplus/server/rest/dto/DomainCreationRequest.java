package org.directtruststandards.timplus.server.rest.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class DomainCreationRequest {
    
    @NotBlank(message = "Domain name is required")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$", 
             message = "Invalid domain name format")
    private String domainName;
    
    public DomainCreationRequest() {}
    
    public DomainCreationRequest(String domainName) {
        this.domainName = domainName;
    }
    
    public String getDomainName() {
        return domainName;
    }
    
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }
}