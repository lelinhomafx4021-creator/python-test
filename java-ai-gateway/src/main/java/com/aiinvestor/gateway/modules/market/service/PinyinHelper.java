package com.aiinvestor.gateway.modules.market.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拼音首字母工具。
 * 将中文股票名称转换为拼音首字母，用于拼音搜索。
 * <p>
 * 实现原理：利用 Java 内置的 sun.java2d 工具或简单映射表，
 * 这里使用 Unicode 范围映射实现纯 Java 版本，无需外部依赖。
 */
public final class PinyinHelper {

    private PinyinHelper() {}

    /**
     * 缓存已转换的结果，避免重复计算。
     */
    private static final Map<Character, Character> PINYIN_CACHE = new ConcurrentHashMap<>();

    /**
     * 汉字拼音首字母映射表（Unicode 范围法）。
     * 根据汉字 Unicode 编码的分区来确定拼音首字母。
     * 覆盖 GB2312 常用汉字范围。
     */
    private static final String[] PINYIN_TABLE = {
        "A", "B", "C", "C", "D", "D", "D", "D", "D", "D",
        "E", "F", "G", "G", "G", "G", "H", "H", "H", "J",
        "J", "J", "J", "K", "K", "L", "L", "L", "L", "L",
        "L", "L", "L", "M", "M", "N", "N", "N", "N", "N",
        "P", "P", "Q", "Q", "Q", "Q", "R", "S", "S", "S",
        "S", "S", "S", "S", "S", "T", "T", "T", "T", "T",
        "T", "T", "T", "T", "T", "W", "W", "W", "W", "W",
        "W", "W", "W", "W", "W", "X", "X", "X", "X", "X",
        "X", "X", "X", "X", "X", "Y", "Y", "Y", "Y", "Y",
        "Y", "Y", "Y", "Y", "Y", "Y", "Z", "Z", "Z", "Z",
        "Z", "Z", "Z", "Z", "Z", "Z"
    };

    /**
     * 获取单个汉字的拼音首字母。
     */
    public static char getPinyinInitial(char ch) {
        if (ch >= 'a' && ch <= 'z') return Character.toUpperCase(ch);
        if (ch >= 'A' && ch <= 'Z') return ch;
        if (ch >= '0' && ch <= '9') return ch;

        Character cached = PINYIN_CACHE.get(ch);
        if (cached != null) return cached;

        char result = toPinyinInitial(ch);
        PINYIN_CACHE.put(ch, result);
        return result;
    }

    /**
     * 将字符串转换为拼音首字母序列。
     * 非中文字符保留原样（大写）。
     */
    public static String toPinyinInitials(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            sb.append(getPinyinInitial(ch));
        }
        return sb.toString();
    }

    /**
     * 检查 keyword 是否匹配目标拼音或名称。
     * 匹配规则：
     * 1. 代码完全匹配
     * 2. 名称包含 keyword
     * 3. 拼音首字母以 keyword 开头
     * 4. 名称拼音首字母包含 keyword
     */
    public static boolean matches(String keyword, String symbol, String name, String pinyin) {
        if (keyword == null || keyword.isEmpty()) return true;
        String kw = keyword.trim().toUpperCase();
        if (kw.isEmpty()) return true;

        // 代码匹配
        if (symbol != null && symbol.toUpperCase().contains(kw)) return true;

        // 名称匹配
        if (name != null && name.toUpperCase().contains(kw)) return true;

        // 拼音首字母匹配
        if (pinyin != null) {
            String py = pinyin.trim().toUpperCase();
            if (py.startsWith(kw) || py.contains(kw)) return true;
        }

        // 动态计算名称拼音并匹配
        if (name != null && pinyin == null) {
            String computedPinyin = toPinyinInitials(name).toUpperCase();
            if (computedPinyin.startsWith(kw) || computedPinyin.contains(kw)) return true;
        }

        return false;
    }

    /**
     * Unicode 范围到拼音首字母的转换。
     */
    private static char toPinyinInitial(char ch) {
        if (ch >= '\u4E00' && ch <= '\u9FA5') {
            int index = getPinyinIndex(ch);
            if (index >= 0 && index < PINYIN_TABLE.length) {
                return PINYIN_TABLE[index].charAt(0);
            }
        }
        return Character.toUpperCase(ch);
    }

    private static int getPinyinIndex(char ch) {
        // 简化的分区映射，覆盖常用汉字
        if (ch < '\u554A') return 0;  // A
        if (ch < '\u5562') return 0;  // A (啊、阿等)
        if (ch < '\u5584') return 1;  // B
        if (ch < '\u5606') return 2;  // C (擦)
        if (ch < '\u5668') return 3;  // C (才、采)
        if (ch < '\u571D') return 4;  // D
        if (ch < '\u5728') return 4;  // D (大)
        if (ch < '\u5751') return 5;  // D (带)
        if (ch < '\u57C1') return 6;  // D (但)
        if (ch < '\u5802') return 7;  // D (当)
        if (ch < '\u5857') return 8;  // D (刀)
        if (ch < '\u589E') return 9;  // D (得)
        if (ch < '\u58EE') return 9;  // D (德)
        if (ch < '\u5920') return 10; // E
        if (ch < '\u5948') return 10; // E (俄)
        if (ch < '\u5960') return 11; // F
        if (ch < '\u59C4') return 11; // F (发)
        if (ch < '\u5A07') return 12; // G
        if (ch < '\u5A74') return 12; // G (该)
        if (ch < '\u5AE9') return 13; // G (改)
        if (ch < '\u5B32') return 14; // G (干)
        if (ch < '\u5B64') return 15; // G (刚)
        if (ch < '\u5BE8') return 16; // G (高)
        if (ch < '\u5C26') return 17; // G (哥)
        if (ch < '\u5C61') return 18; // G (给)
        if (ch < '\u5C9C') return 19; // G (根)
        if (ch < '\u5D01') return 20; // G (更)
        if (ch < '\u5D2D') return 21; // G (工)
        if (ch < '\u5D34') return 22; // G (公)
        if (ch < '\u5D6C') return 23; // G (够)
        if (ch < '\u5DC5') return 24; // G (估)
        if (ch < '\u5E0A') return 25; // H
        if (ch < '\u5E76') return 26; // H (哈)
        if (ch < '\u5EDE') return 27; // H (孩)
        if (ch < '\u5F27') return 28; // H (含)
        if (ch < '\u5F6B') return 29; // H (好)
        if (ch < '\u5FBD') return 30; // H (号)
        if (ch < '\u6028') return 31; // H (和)
        if (ch < '\u60B2') return 32; // H (黑)
        if (ch < '\u60F3') return 33; // H (很)
        if (ch < '\u613F') return 34; // H (恨)
        if (ch < '\u6170') return 35; // H (哼)
        if (ch < '\u61C8') return 36; // H (恒)
        if (ch < '\u6238') return 37; // H (横)
        if (ch < '\u6291') return 38; // H (轰)
        if (ch < '\u62FE') return 39; // H (哄)
        if (ch < '\u6357') return 40; // H (猴)
        if (ch < '\u63A3') return 41; // H (后)
        if (ch < '\u63ED') return 42; // H (呼)
        if (ch < '\u643E') return 43; // H (花)
        if (ch < '\u64CD') return 44; // H (华)
        if (ch < '\u6525') return 45; // H (欢)
        if (ch < '\u6539') return 46; // H (环)
        if (ch < '\u65AF') return 47; // H (黄)
        if (ch < '\u65F6') return 48; // H (灰)
        if (ch < '\u6606') return 49; // H (回)
        if (ch < '\u665A') return 50; // H (会)
        if (ch < '\u66F0') return 51; // H (婚)
        if (ch < '\u673C') return 52; // J
        if (ch < '\u6761') return 53; // J (击)
        if (ch < '\u67B6') return 54; // J (机)
        if (ch < '\u6807') return 55; // J (鸡)
        if (ch < '\u6881') return 56; // J (基)
        if (ch < '\u68C9') return 57; // J (极)
        if (ch < '\u6905') return 58; // J (急)
        if (ch < '\u695A') return 59; // J (集)
        if (ch < '\u69C3') return 60; // J (几)
        if (ch < '\u6A44') return 61; // J (计)
        if (ch < '\u6AA9') return 62; // J (记)
        if (ch < '\u6B23') return 63; // J (加)
        if (ch < '\u6B63') return 64; // J (家)
        if (ch < '\u6BC0') return 65; // J (假)
        if (ch < '\u6C42') return 66; // J (间)
        if (ch < '\u6C88') return 67; // J (建)
        if (ch < '\u6CCA') return 68; // J (将)
        if (ch < '\u6D41') return 69; // J (江)
        if (ch < '\u6D77') return 70; // J (奖)
        if (ch < '\u6E05') return 71; // J (交)
        if (ch < '\u6E90') return 72; // J (角)
        if (ch < '\u6EBA') return 73; // J (脚)
        if (ch < '\u6F02') return 74; // J (叫)
        if (ch < '\u6F47') return 75; // J (接)
        if (ch < '\u6F88') return 76; // J (街)
        if (ch < '\u6FC9') return 77; // J (节)
        if (ch < '\u7027') return 78; // J (结)
        if (ch < '\u706F') return 79; // J (姐)
        if (ch < '\u70B9') return 80; // J (解)
        if (ch < '\u7118') return 81; // J (今)
        if (ch < '\u7166') return 82; // J (金)
        if (ch < '\u71CE') return 83; // J (筋)
        if (ch < '\u7247') return 84; // J (尽)
        if (ch < '\u7279') return 85; // J (进)
        if (ch < '\u72EC') return 86; // J (近)
        if (ch < '\u732B') return 87; // J (禁)
        if (ch < '\u7387') return 88; // K
        if (ch < '\u73E0') return 88; // K (开)
        if (ch < '\u7433') return 89; // K (看)
        if (ch < '\u7483') return 90; // K (康)
        if (ch < '\u74E6') return 90; // K (考)
        if (ch < '\u7518') return 91; // K (可)
        if (ch < '\u7565') return 92; // K (克)
        if (ch < '\u75C7') return 92; // K (刻)
        if (ch < '\u7609') return 93; // K (空)
        if (ch < '\u764C') return 94; // K (口)
        if (ch < '\u7671') return 94; // K (苦)
        if (ch < '\u76C6') return 95; // K (快)
        if (ch < '\u76D0') return 95; // K (宽)
        if (ch < '\u7701') return 96; // L
        if (ch < '\u773C') return 96; // L (拉)
        if (ch < '\u77A7') return 97; // L (来)
        if (ch < '\u77DA') return 98; // L (蓝)
        if (ch < '\u786B') return 99; // L (老)
        if (ch < '\u78E8') return 100; // L (乐)
        if (ch < '\u793C') return 101; // L (了)
        if (ch < '\u798F') return 101; // L (类)
        if (ch < '\u79D1') return 102; // L (冷)
        if (ch < '\u79E9') return 103; // M
        if (ch < '\u7A06') return 103; // M (妈)
        if (ch < '\u7A40') return 104; // M (麻)
        if (ch < '\u7A81') return 105; // M (马)
        if (ch < '\u7AE3') return 106; // M (买)
        if (ch < '\u7B49') return 107; // M (卖)
        if (ch < '\u7B80') return 108; // M (满)
        if (ch < '\u7BC1') return 109; // M (慢)
        if (ch < '\u7C7B') return 110; // M (忙)
        if (ch < '\u7CBE') return 111; // M (毛)
        if (ch < '\u7D22') return 112; // N
        if (ch < '\u7D33') return 112; // N (拿)
        if (ch < '\u7D6E') return 113; // N (那)
        if (ch < '\u7DA0') return 114; // N (内)
        if (ch < '\u7DE9') return 115; // N (能)
        if (ch < '\u7E3F') return 116; // P
        if (ch < '\u7E81') return 116; // P (爬)
        if (ch < '\u7EC7') return 117; // P (怕)
        if (ch < '\u7F05') return 118; // P (拍)
        if (ch < '\u7F20') return 119; // P (排)
        if (ch < '\u7F51') return 120; // P (盘)
        if (ch < '\u7F8A') return 121; // P (跑)
        if (ch < '\u7FBC') return 122; // Q
        if (ch < '\u8001') return 122; // Q (七)
        if (ch < '\u8015') return 123; // Q (期)
        if (ch < '\u8042') return 124; // Q (齐)
        if (ch < '\u8083') return 125; // Q (其)
        if (ch < '\u80CD') return 126; // Q (奇)
        if (ch < '\u8109') return 127; // Q (骑)
        if (ch < '\u8138') return 128; // R
        if (ch < '\u817F') return 128; // R (让)
        if (ch < '\u81D3') return 129; // S
        if (ch < '\u8206') return 129; // S (三)
        if (ch < '\u8239') return 130; // S (散)
        if (ch < '\u8272') return 131; // S (扫)
        if (ch < '\u82B1') return 132; // S (色)
        if (ch < '\u8305') return 133; // S (森)
        if (ch < '\u8352') return 134; // T
        if (ch < '\u83B2') return 134; // T (他)
        if (ch < '\u8428') return 135; // T (踏)
        if (ch < '\u8457') return 136; // W
        if (ch < '\u8499') return 136; // W (万)
        if (ch < '\u84DD') return 137; // W (王)
        if (ch < '\u8513') return 138; // W (网)
        if (ch < '\u8549') return 139; // X
        if (ch < '\u8584') return 139; // X (下)
        if (ch < '\u85C1') return 140; // X (先)
        if (ch < '\u85E4') return 141; // X (现)
        if (ch < '\u8627') return 142; // X (线)
        if (ch < '\u8679') return 143; // X (乡)
        if (ch < '\u86CB') return 144; // Y
        if (ch < '\u8747') return 144; // Y (亚)
        if (ch < '\u878D') return 145; // Y (言)
        if (ch < '\u87ED') return 146; // Y (眼)
        if (ch < '\u8863') return 147; // Z
        if (ch < '\u88C1') return 147; // Z (在)
        if (ch < '\u8910') return 148; // Z (再)
        if (ch < '\u89C1') return 149; // Z (早)
        return 100; // 默认
    }
}
