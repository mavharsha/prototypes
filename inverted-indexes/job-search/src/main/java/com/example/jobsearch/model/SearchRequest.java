package com.example.jobsearch.model;

import java.util.List;

/**
 * Search request with filters
 */
public class SearchRequest {

    private String query;
    private List<String> locations;
    private List<Job.JobType> jobTypes;
    private List<Job.ExperienceLevel> experienceLevels;
    private Integer salaryMin;
    private Integer salaryMax;
    private Boolean remote;
    private List<String> skills;
    private String sortBy = "relevance"; // relevance, date, salaryAsc, salaryDesc
    private int page = 0;
    private int size = 10;

    // Default constructor
    public SearchRequest() {}

    // Getters and Setters
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<String> getLocations() { return locations; }
    public void setLocations(List<String> locations) { this.locations = locations; }

    public List<Job.JobType> getJobTypes() { return jobTypes; }
    public void setJobTypes(List<Job.JobType> jobTypes) { this.jobTypes = jobTypes; }

    public List<Job.ExperienceLevel> getExperienceLevels() { return experienceLevels; }
    public void setExperienceLevels(List<Job.ExperienceLevel> experienceLevels) {
        this.experienceLevels = experienceLevels;
    }

    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }

    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }

    public Boolean getRemote() { return remote; }
    public void setRemote(Boolean remote) { this.remote = remote; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
