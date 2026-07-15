package com.api.blueprint.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Pet {
    private Long id;
    private Category category;
    private String name;
    private String[] photoUrls;
    private List<Tag> tags;
    private String status;

    public Pet() {}

    @JsonCreator
    public Pet(
            @JsonProperty("id") Long id,
            @JsonProperty("category") Category category,
            @JsonProperty("name") String name,
            @JsonProperty("photoUrls") String[] photoUrls,
            @JsonProperty("tags") List<Tag> tags,
            @JsonProperty("status") String status) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.photoUrls = photoUrls;
        this.tags = tags;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(String[] photoUrls) {
        this.photoUrls = photoUrls;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
