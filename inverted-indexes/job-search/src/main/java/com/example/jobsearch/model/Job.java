package com.example.jobsearch.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

/**
 * Job listing model
 */
public class Job {

    private String id;
    private String title;
    private String company;
    private String description;
    private String location;
    private JobType jobType;
    private ExperienceLevel experienceLevel;
    private int salaryMin;
    private int salaryMax;
    private String[] skills;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate postedDate;

    private boolean remote;

    // Enums
    public enum JobType {
        FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP
    }

    public enum ExperienceLevel {
        ENTRY, MID, SENIOR, LEAD, EXECUTIVE
    }

    // Default constructor
    public Job() {}

    // Full constructor
    public Job(String id, String title, String company, String description,
               String location, JobType jobType, ExperienceLevel experienceLevel,
               int salaryMin, int salaryMax, String[] skills,
               LocalDate postedDate, boolean remote) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.description = description;
        this.location = location;
        this.jobType = jobType;
        this.experienceLevel = experienceLevel;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.skills = skills;
        this.postedDate = postedDate;
        this.remote = remote;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public JobType getJobType() { return jobType; }
    public void setJobType(JobType jobType) { this.jobType = jobType; }

    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(ExperienceLevel experienceLevel) { this.experienceLevel = experienceLevel; }

    public int getSalaryMin() { return salaryMin; }
    public void setSalaryMin(int salaryMin) { this.salaryMin = salaryMin; }

    public int getSalaryMax() { return salaryMax; }
    public void setSalaryMax(int salaryMax) { this.salaryMax = salaryMax; }

    public String[] getSkills() { return skills; }
    public void setSkills(String[] skills) { this.skills = skills; }

    public LocalDate getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDate postedDate) { this.postedDate = postedDate; }

    public boolean isRemote() { return remote; }
    public void setRemote(boolean remote) { this.remote = remote; }

    @Override
    public String toString() {
        return String.format("Job{id='%s', title='%s', company='%s', location='%s'}",
            id, title, company, location);
    }
}
