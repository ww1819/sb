package com.meis.saas.common.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public final class PinyinCodeUtil {
    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private PinyinCodeUtil() {}

    /** 根据名称生成拼音简码（汉字取首字母，英文数字保留） */
    public static String toShortCode(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            if (c <= 127) {
                if (Character.isLetterOrDigit(c)) {
                    sb.append(Character.toLowerCase(c));
                }
                continue;
            }
            try {
                String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                if (arr != null && arr.length > 0 && !arr[0].isBlank()) {
                    sb.append(arr[0].charAt(0));
                }
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                // skip
            }
        }
        return sb.toString();
    }
}
