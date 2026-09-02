package com.schoolai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolai.config.WebSearchProperties;
import com.schoolai.model.vo.WebSearchResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSearchServiceTest {

    @Test
    void rankAndSelect_shouldPreferTrustedHitsAndFillAtLeastThreeResults() {
        WebSearchProperties properties = new WebSearchProperties();
        properties.setAllowedDomains(List.of("edu.cn", "gov.cn"));
        WebSearchService service = new WebSearchService(properties, new ObjectMapper());

        WebSearchResultVO trustedLow = result("https://notice.edu.cn/a", "可信结果", 0.4);
        WebSearchResultVO trustedHigh = result("https://notice.gov.cn/b", "高分可信结果", 0.9);
        WebSearchResultVO publicHigh = result("https://news.example.com/c", "补足结果", 0.8);
        WebSearchResultVO duplicate = result("https://news.example.com/c#top", "重复结果", 0.7);

        List<WebSearchResultVO> selected = service.rankAndSelect(
                List.of(trustedLow, trustedHigh, publicHigh, duplicate), 5);

        assertEquals(3, selected.size());
        assertEquals("高分可信结果", selected.get(0).getTitle());
        assertEquals("可信结果", selected.get(1).getTitle());
        assertEquals("补足结果", selected.get(2).getTitle());
    }

    @Test
    void rankAndSelect_shouldRespectDisplayLimit() {
        WebSearchProperties properties = new WebSearchProperties();
        WebSearchService service = new WebSearchService(properties, new ObjectMapper());

        List<WebSearchResultVO> selected = service.rankAndSelect(List.of(
                result("https://a.example/a", "一", 0.1),
                result("https://b.example/b", "二", 0.9),
                result("https://c.example/c", "三", 0.8),
                result("https://d.example/d", "四", 0.7)), 3);

        assertEquals(3, selected.size());
        assertEquals("二", selected.get(0).getTitle());
    }

    @Test
    void rankAndSelect_shouldKeepBochaOrderWhenScoresTieAndDropQueryDuplicates() {
        WebSearchProperties properties = new WebSearchProperties();
        WebSearchService service = new WebSearchService(properties, new ObjectMapper());

        List<WebSearchResultVO> selected = service.rankAndSelect(List.of(
                result("https://news.example.com/hot?id=1", "博查热门结果", 0),
                result("https://news.example.com/hot?bz=1", "同页重复结果", 0),
                result("https://news.example.com/second", "第二结果", 0),
                result("https://news.example.com/third", "第三结果", 0)), 5);

        assertEquals(3, selected.size());
        assertEquals("博查热门结果", selected.get(0).getTitle());
    }

    private WebSearchResultVO result(String url, String title, double score) {
        WebSearchResultVO result = new WebSearchResultVO();
        result.setUrl(url);
        result.setTitle(title);
        result.setScore(score);
        return result;
    }
}
