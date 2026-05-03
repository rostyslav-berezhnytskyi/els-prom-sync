package com.els.promsync.dto;

import java.util.List;
import java.util.Map;

public record AiProductResponse(
        String seoName,
        String seoDescription,
        List<String> keywords,
        Map<String, String> specs,
        String vendor
) {}
