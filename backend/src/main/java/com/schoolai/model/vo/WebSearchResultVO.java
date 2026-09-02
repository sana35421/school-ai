package com.schoolai.model.vo;

import lombok.Data;

@Data
public class WebSearchResultVO {
    private String title;
    private String url;
    private String content;
    private double score;
}
