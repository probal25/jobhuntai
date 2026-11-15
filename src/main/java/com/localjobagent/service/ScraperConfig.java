package com.localjobagent.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/**
 * Configuration properties for the Job Scraper.
 * Using @ConfigurationProperties is the most robust way to handle Lists in Spring Boot.
 */
@Configuration
@ConfigurationProperties(prefix = "job.scraper")
public class ScraperConfig {
    private List<String> endpointList;
    private String containerSelector;
    private String titleSelector;
    private String descriptionSelector;
    private String linkSelector;

    // Getters and Setters are required for @ConfigurationProperties
    public List<String> getEndpointList() { return endpointList; }
    public void setEndpointList(List<String> endpointList) { this.endpointList = endpointList; }

    public String getContainerSelector() { return containerSelector; }
    public void setContainerSelector(String containerSelector) { this.containerSelector = containerSelector; }

    public String getTitleSelector() { return titleSelector; }
    public void setTitleSelector(String titleSelector) { this.titleSelector = titleSelector; }

    public String getDescriptionSelector() { return descriptionSelector; }
    public void setDescriptionSelector(String descriptionSelector) { this.descriptionSelector = descriptionSelector; }

    public String getLinkSelector() { return linkSelector; }
    public void setLinkSelector(String linkSelector) { this.linkSelector = linkSelector; }
}
