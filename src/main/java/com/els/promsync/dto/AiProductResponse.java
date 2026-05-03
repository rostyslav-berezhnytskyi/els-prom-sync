package com.els.promsync.dto;

import java.util.List;
import java.util.Map;

public record AiProductResponse(
        String seoNameUa,
        String seoNameRu,
        String seoDescriptionUa,
        String seoDescriptionRu,
        List<String> keywordsUa,
        List<String> keywordsRu,
        Map<String, String> specs,
        String vendor
) {}
