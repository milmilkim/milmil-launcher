package com.milmil.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 런처 설정/상태 저장소. SharedPreferences만 사용한다. */
public class LauncherStore {

    /** 패널 스타일: 0 투명, 1 반투명, 2 종이, 3 액자 */
    static final int STYLE_TRANSPARENT = 0;
    static final int STYLE_TRANSLUCENT = 1;
    static final int STYLE_PAPER = 2;
    static final int STYLE_FRAME = 3;

    /** 패널 크기: 0 = 2줄, 1 = 3줄, 2 = 4줄, 3 = 전체 화면 */
    static final int SIZE_TWO_ROWS = 0;
    static final int SIZE_THREE_ROWS = 1;
    static final int SIZE_FOUR_ROWS = 2;
    static final int SIZE_FULL = 3;

    /** 패널 위치(전체 화면 외): 0 = 상, 1 = 중, 2 = 하 */
    static final int POS_TOP = 0;
    static final int POS_MIDDLE = 1;
    static final int POS_BOTTOM = 2;

    private final SharedPreferences prefs;

    LauncherStore(Context context) {
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    int panelPosition() {
        return prefs.getInt("panelPos", POS_BOTTOM);
    }

    void setPanelPosition(int pos) {
        prefs.edit().putInt("panelPos", pos).apply();
    }

    int panelStyle() {
        return prefs.getInt("panelStyle", STYLE_TRANSPARENT);
    }

    void setPanelStyle(int style) {
        prefs.edit().putInt("panelStyle", style).apply();
    }

    int panelSize() {
        if (prefs.contains("panelSize2")) {
            return prefs.getInt("panelSize2", SIZE_FOUR_ROWS);
        }
        // 구버전 값 마이그레이션 (0=2줄, 1=4줄, 2=전체)
        switch (prefs.getInt("panelSize", -1)) {
            case 0:
                return SIZE_TWO_ROWS;
            case 2:
                return SIZE_FULL;
            case 1:
            default:
                return SIZE_FOUR_ROWS;
        }
    }

    void setPanelSize(int size) {
        prefs.edit().putInt("panelSize2", size).apply();
    }

    /** 전체 앱 표시 순서(숨김 앱 포함). 항목은 "패키지명/액티비티명" 키. */
    List<String> homeOrder() {
        String joined = prefs.getString("homeOrder", "");
        if (joined.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(joined.split("\n")));
    }

    void saveHomeOrder(List<String> keys) {
        prefs.edit().putString("homeOrder", TextUtils.join("\n", keys)).apply();
    }

    /** 런처 화면에서 상단바(상태바) 숨김 여부 */
    boolean hideStatusBar() {
        return prefs.getBoolean("hideStatusBar", false);
    }

    void setHideStatusBar(boolean hide) {
        prefs.edit().putBoolean("hideStatusBar", hide).apply();
    }

    /** 아이콘 크기: 0 = 보통, 1 = 크게 */
    static final int ICON_NORMAL = 0;
    static final int ICON_LARGE = 1;

    int iconSize() {
        return prefs.getInt("iconSize", ICON_NORMAL);
    }

    void setIconSize(int size) {
        prefs.edit().putInt("iconSize", size).apply();
    }

    /** 앱별 커스텀 아이콘 파일명 ("icon.<앱키>" → 보관함 파일명) */
    String customIcon(String appKey) {
        return prefs.getString("icon." + appKey, null);
    }

    void setCustomIcon(String appKey, String fileName) {
        prefs.edit().putString("icon." + appKey, fileName).apply();
    }

    void clearCustomIcon(String appKey) {
        prefs.edit().remove("icon." + appKey).apply();
    }

    /** 특정 보관함 이미지를 쓰고 있는 앱 키 목록 */
    List<String> appKeysUsingIcon(String fileName) {
        List<String> keys = new ArrayList<>();
        for (String prefKey : prefs.getAll().keySet()) {
            if (prefKey.startsWith("icon.")
                    && fileName.equals(prefs.getString(prefKey, null))) {
                keys.add(prefKey.substring("icon.".length()));
            }
        }
        return keys;
    }

    /** 숨긴 앱 키 목록 */
    Set<String> hiddenKeys() {
        String joined = prefs.getString("hidden", "");
        Set<String> keys = new LinkedHashSet<>();
        if (!joined.isEmpty()) {
            keys.addAll(Arrays.asList(joined.split("\n")));
        }
        return keys;
    }

    void saveHiddenKeys(Set<String> keys) {
        prefs.edit().putString("hidden", TextUtils.join("\n", keys)).apply();
    }
}
