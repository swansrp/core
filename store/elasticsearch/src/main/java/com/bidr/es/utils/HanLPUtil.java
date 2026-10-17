package com.bidr.es.utils;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.py.Pinyin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: HanLPUtil
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 9:35
 */

public class HanLPUtil {

    public static List<String> tokenize(String text, boolean pinyin) {
        List<String> tokens = tokenize(text);
        if (pinyin) {
            tokens.addAll(toPinyinFull(text));
            tokens.addAll(toPinyinShort(text));
        }
        return tokens;
    }

    /**
     * 分词（示例用HanLP简单分词）
     */
    public static List<String> tokenize(String text) {
        // 简单分词
        return HanLP.segment(text).stream().map(term -> term.word).collect(Collectors.toList());
    }

    /**
     * 获取拼音全拼
     */
    public static List<String> toPinyinFull(String text) {
        List<Pinyin> pinyinList = HanLP.convertToPinyinList(text);
        List<String> fullPinyin = new ArrayList<>();
        for (Pinyin pinyin : pinyinList) {
            fullPinyin.add(pinyin.getPinyinWithoutTone());
        }
        return fullPinyin;
    }

    /**
     * 获取拼音简拼（首字母）
     */
    public static List<String> toPinyinShort(String text) {
        List<Pinyin> pinyinList = HanLP.convertToPinyinList(text);
        List<String> shortPinyin = new ArrayList<>();
        for (Pinyin pinyin : pinyinList) {
            String py = pinyin.getPinyinWithoutTone();
            if (py.length() > 0) {
                shortPinyin.add(py.substring(0, 1));
            }
        }
        return shortPinyin;
    }
}
