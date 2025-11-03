package org.directtruststandards.timplus.server.rest.dto;

public class DomainCreationResponse {
    
    private boolean success;
    private String message;
    private String domainName;
    
    public DomainCreationResponse() {}
    
    public DomainCreationResponse(boolean success, String message, String domainName) {
        this.success = success;
        this.message = message;
        this.domainName = domainName;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getDomainName() {
        return domainName;
    }
    
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }
}