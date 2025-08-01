package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.mapping.*;

import java.util.Map;
import java.util.Objects;

/**
 * Title: MappingComparatorUtil
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 14:54
 */

public class MappingComparator {
    public static boolean compare(Map<String, Property> a, Map<String, Property> b) {
        if (a.size() != b.size()) {
            return false;
        }

        for (String key : a.keySet()) {
            if (!b.containsKey(key)) {
                return false;
            }

            Property p1 = a.get(key);
            Property p2 = b.get(key);

            if (!Objects.equals(p1._kind(), p2._kind())) {
                return false;
            }

            switch (p1._kind()) {
                case Keyword:
                    if (!compareKeyword(p1.keyword(), p2.keyword())) {
                        return false;
                    }
                    break;
                case Text:
                    if (!compareText(p1.text(), p2.text())) {
                        return false;
                    }
                    break;
                case Completion:
                    if (!compareCompletion(p1.completion(), p2.completion())) {
                        return false;
                    }
                    break;
                case Date:
                    if (!compareDate(p1.date(), p2.date())) {
                        return false;
                    }
                    break;
                case Boolean:
                    // nothing to compare
                    break;
                default:
                    // 不支持的类型
                    return false;
            }
        }

        return true;
    }

    private static boolean compareKeyword(KeywordProperty p1, KeywordProperty p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        return Objects.equals(p1.ignoreAbove(), p2.ignoreAbove()) && Objects.equals(p1.index(), p2.index());
    }

    private static boolean compareText(TextProperty p1, TextProperty p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        return Objects.equals(p1.analyzer(), p2.analyzer()) && Objects.equals(p1.searchAnalyzer(), p2.searchAnalyzer());
    }

    private static boolean compareCompletion(CompletionProperty p1, CompletionProperty p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        return Objects.equals(p1.analyzer(), p2.analyzer()) && Objects.equals(p1.searchAnalyzer(), p2.searchAnalyzer());
    }

    private static boolean compareDate(DateProperty p1, DateProperty p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        return Objects.equals(p1.format(), p2.format());
    }
}
