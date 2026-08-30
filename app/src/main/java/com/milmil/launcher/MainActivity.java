package com.milmil.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    static final int COLUMNS = 4;

    /** LauncherStore.SIZE_* 인덱스 순서: 2줄, 3줄, 4줄, 전체 화면 */
    private static final int[] SIZE_ROWS = {2, 3, 4, 6};
    private static final int[] SIZE_PANEL_WEIGHT = {33, 47, 60, 100};
    private static final String[] SIZE_LABELS = {"2줄", "3줄", "4줄", "전체 화면"};
    private static final String[] STYLE_LABELS = {"투명", "반투명", "종이", "액자"};

    /** LauncherStore.ICON_* 인덱스 순서: 보통, 크게 */
    private static final int[] ICON_SIZE_DP = {48, 60};
    private static final String[] ICON_SIZE_LABELS = {"보통", "크게"};

    /** 전체 앱(숨김 포함) 표시 순서. 저장되는 homeOrder의 원본. */
    private final List<AppInfo> allOrdered = new ArrayList<>();
    /** 홈에 실제 표시되는 앱 (allOrdered - 숨김) */
    private final List<AppInfo> visibleApps = new ArrayList<>();
    private Set<String> hiddenKeys = new HashSet<>();

    private LauncherStore store;
    private View wallpaperArea;
    private LinearLayout appPanel;
    private GridLayout grid;
    private AppGridRenderer renderer;
    private TextView pageIndicator;
    private int currentPage = 0;
    private int pageSize = COLUMNS * SIZE_ROWS[LauncherStore.SIZE_FOUR_ROWS];

    /** 이동 모드에서 선택된 앱. null이면 일반 모드. */
    private AppInfo movingApp;

    private static final int REQUEST_PICK_WALLPAPER = 1;
    private static final int REQUEST_PICK_ICON = 2;
    private static final int ICON_SIZE = 144;
    private HomeRootLayout homeRoot;
    /** 아이콘 보관함에 이미지 추가 후 돌아올 앱 */
    private AppInfo pendingIconApp;

    /**
     * 앱 설치/삭제/변경 시 목록 갱신. 런처 프로세스가 살아 있는 동안에만 수신하며
     * (매니페스트 등록 아님) 백그라운드 작업을 만들지 않는다.
     */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            movingApp = null;
            loadApps();
            clampCurrentPage();
            renderCurrentPage();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new LauncherStore(this);
        wallpaperArea = findViewById(R.id.wallpaper_area);
        appPanel = findViewById(R.id.app_panel);
        grid = findViewById(R.id.app_grid);
        pageIndicator = findViewById(R.id.page_indicator);

        HomeRootLayout root = findViewById(R.id.home_root);
        homeRoot = root;
        root.setPageKeyListener(keyCode -> {
            if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                previousPage();
            } else {
                nextPage();
            }
            return true;
        });
        // 루트가 항상 포커스를 쥐고 있어야 물리키가 preIme 단계에서 잡힌다
        root.requestFocus();
        // 빈 영역(인디케이터 포함) 길게 누르면 런처 설정
        root.setOnLongClickListener(v -> {
            showSettingsDialog();
            return true;
        });

        // 좌우 스와이프로 페이지 전환 (즉시 교체, 애니메이션 없음)
        root.setSwipeListener(toNext -> {
            if (toNext) {
                nextPage();
            } else {
                previousPage();
            }
        });

        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");
        registerReceiver(packageReceiver, packageFilter);

        loadApps();
        applyPanelConfig();
        applyWallpaper();
        applyStatusBarConfig();
    }

    /**
     * 상단바 숨김 설정을 창에 반영한다. 런처 화면에서만 적용된다.
     * 컨텐츠는 항상 상단바 뒤까지 깔리므로(투명 상단바 오버레이) 토글해도
     * 배경화면 크기와 레이아웃이 변하지 않는다.
     */
    private void applyStatusBarConfig() {
        View decor = getWindow().getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (store.hideStatusBar()) {
            flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        decor.setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(0x00000000);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // immersive 상태는 포커스를 잃으면 풀리므로 되돌아올 때 다시 적용한다
        if (hasFocus) {
            applyStatusBarConfig();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    /** 설치된 런처 앱을 읽고 저장된 순서/숨김을 반영한다. 홈 복귀 시 재스캔하지 않는다. */
    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);

        Map<String, AppInfo> installed = new LinkedHashMap<>();
        List<AppInfo> byLabel = new ArrayList<>();
        for (ResolveInfo ri : resolved) {
            if (getPackageName().equals(ri.activityInfo.packageName)) {
                continue;
            }
            AppInfo app = new AppInfo(
                    String.valueOf(ri.loadLabel(pm)),
                    ri.activityInfo.packageName,
                    ri.activityInfo.name);
            installed.put(app.key(), app);
            byLabel.add(app);
        }
        Collator collator = Collator.getInstance();
        Collections.sort(byLabel, (a, b) -> collator.compare(a.label, b.label));

        // 저장된 순서 먼저, 새로 설치된 앱은 가나다순으로 뒤에 붙인다
        allOrdered.clear();
        Set<String> placed = new HashSet<>();
        for (String key : store.homeOrder()) {
            AppInfo app = installed.get(key);
            if (app != null && placed.add(key)) {
                allOrdered.add(app);
            }
        }
        for (AppInfo app : byLabel) {
            if (placed.add(app.key())) {
                allOrdered.add(app);
            }
        }
        saveOrder();

        // 삭제된 앱은 숨김 목록에서도 정리
        hiddenKeys = store.hiddenKeys();
        if (hiddenKeys.retainAll(installed.keySet())) {
            store.saveHiddenKeys(hiddenKeys);
        }
        rebuildVisibleApps();
    }

    private void saveOrder() {
        List<String> keys = new ArrayList<>(allOrdered.size());
        for (AppInfo app : allOrdered) {
            keys.add(app.key());
        }
        store.saveHomeOrder(keys);
    }

    private void rebuildVisibleApps() {
        visibleApps.clear();
        for (AppInfo app : allOrdered) {
            if (!hiddenKeys.contains(app.key())) {
                visibleApps.add(app);
            }
        }
    }

    /** 패널 크기/스타일 설정을 화면에 반영하고 그리드를 다시 만든다. */
    private void applyPanelConfig() {
        int size = store.panelSize();
        int rows = SIZE_ROWS[size];
        pageSize = COLUMNS * rows;

        LinearLayout.LayoutParams wallpaperLp =
                (LinearLayout.LayoutParams) wallpaperArea.getLayoutParams();
        wallpaperLp.weight = 100 - SIZE_PANEL_WEIGHT[size];
        wallpaperArea.setLayoutParams(wallpaperLp);

        LinearLayout.LayoutParams panelLp =
                (LinearLayout.LayoutParams) appPanel.getLayoutParams();
        panelLp.weight = SIZE_PANEL_WEIGHT[size];
        appPanel.setLayoutParams(panelLp);

        grid.removeAllViews();
        int iconSizePx = (int) (ICON_SIZE_DP[store.iconSize()]
                * getResources().getDisplayMetrics().density);
        renderer = new AppGridRenderer(this, grid, COLUMNS, rows, iconSizePx);

        applyPanelStyle();
        clampCurrentPage();
        renderCurrentPage();
    }

    private void applyPanelStyle() {
        switch (store.panelStyle()) {
            case LauncherStore.STYLE_TRANSLUCENT:
                appPanel.setBackgroundResource(R.drawable.panel_translucent);
                break;
            case LauncherStore.STYLE_PAPER:
                appPanel.setBackgroundResource(R.drawable.panel_paper);
                break;
            case LauncherStore.STYLE_FRAME:
                appPanel.setBackgroundResource(R.drawable.panel_frame);
                break;
            default:
                appPanel.setBackground(null);
                break;
        }
    }

    // ---- 셀 입력 (AppGridRenderer가 호출) ----

    void onCellTapped(int cellIndex) {
        int index = currentPage * pageSize + cellIndex;
        if (movingApp != null) {
            if (index >= visibleApps.size()) {
                index = visibleApps.size() - 1;
            }
            int from = visibleApps.indexOf(movingApp);
            if (from >= 0 && from != index) {
                moveVisibleApp(from, index);
            }
            movingApp = null;
            renderCurrentPage();
            return;
        }
        if (index < visibleApps.size()) {
            launchApp(visibleApps.get(index));
        }
    }

    boolean onCellLongPressed(int cellIndex) {
        if (movingApp != null) {
            return true;
        }
        int index = currentPage * pageSize + cellIndex;
        if (index >= visibleApps.size()) {
            return false;
        }
        showAppMenu(visibleApps.get(index));
        return true;
    }

    /** 정적 삽입 이동: from의 앱을 to 위치에 끼워 넣고 사이 앱들을 민다. */
    private void moveVisibleApp(int from, int to) {
        AppInfo moving = visibleApps.remove(from);
        visibleApps.add(to, moving);

        // 숨김 앱 위치를 보존하면서 전체 순서(allOrdered)에 반영
        allOrdered.remove(moving);
        int insertIndex;
        if (to == 0) {
            insertIndex = visibleApps.size() > 1 ? allOrdered.indexOf(visibleApps.get(1)) : 0;
        } else {
            insertIndex = allOrdered.indexOf(visibleApps.get(to - 1)) + 1;
        }
        allOrdered.add(insertIndex, moving);
        saveOrder();
    }

    // ---- 앱별 메뉴 ----

    private void showAppMenu(AppInfo app) {
        String[] items = {"열기", "이동", "숨기기", "아이콘 변경", "앱 정보", "런처 설정"};
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            launchApp(app);
                            break;
                        case 1:
                            movingApp = app;
                            renderCurrentPage();
                            break;
                        case 2:
                            hideApp(app);
                            break;
                        case 3:
                            showIconLibraryDialog(app);
                            break;
                        case 4:
                            openAppInfo(app);
                            break;
                        case 5:
                            showSettingsDialog();
                            break;
                    }
                })
                .show();
    }

    private void hideApp(AppInfo app) {
        hiddenKeys.add(app.key());
        store.saveHiddenKeys(hiddenKeys);
        rebuildVisibleApps();
        clampCurrentPage();
        renderCurrentPage();
    }

    private void openAppInfo(AppInfo app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.packageName));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // 설정 화면이 없는 기기라면 무시
        }
    }

    // ---- 런처 설정 ----

    private void showSettingsDialog() {
        String[] items = {"배경화면 선택", "배경화면 제거", "패널 크기", "패널 스타일",
                "아이콘 크기",
                store.hideStatusBar() ? "상단바 표시" : "상단바 숨기기",
                "앱 숨김 관리", "앱 순서 초기화"};
        new AlertDialog.Builder(this)
                .setTitle("런처 설정")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            pickWallpaper();
                            break;
                        case 1:
                            removeWallpaper();
                            break;
                        case 2:
                            showPanelSizeDialog();
                            break;
                        case 3:
                            showPanelStyleDialog();
                            break;
                        case 4:
                            showIconSizeDialog();
                            break;
                        case 5:
                            store.setHideStatusBar(!store.hideStatusBar());
                            applyStatusBarConfig();
                            break;
                        case 6:
                            showHiddenAppsDialog();
                            break;
                        case 7:
                            showResetOrderDialog();
                            break;
                    }
                })
                .show();
    }

    private void showPanelSizeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("패널 크기")
                .setSingleChoiceItems(SIZE_LABELS, store.panelSize(), (dialog, which) -> {
                    store.setPanelSize(which);
                    applyPanelConfig();
                    dialog.dismiss();
                })
                .show();
    }

    private void showPanelStyleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("패널 스타일")
                .setSingleChoiceItems(STYLE_LABELS, store.panelStyle(), (dialog, which) -> {
                    store.setPanelStyle(which);
                    applyPanelStyle();
                    dialog.dismiss();
                })
                .show();
    }

    private void showIconSizeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("아이콘 크기")
                .setSingleChoiceItems(ICON_SIZE_LABELS, store.iconSize(), (dialog, which) -> {
                    store.setIconSize(which);
                    applyPanelConfig();
                    dialog.dismiss();
                })
                .show();
    }

    /** 전체 앱 목록에서 체크된 앱을 숨긴다. 플랫폼 AlertDialog 리스트라 스크롤도 기본 지원. */
    private void showHiddenAppsDialog() {
        final List<AppInfo> apps = new ArrayList<>(allOrdered);
        String[] labels = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            checked[i] = hiddenKeys.contains(apps.get(i).key());
        }
        new AlertDialog.Builder(this)
                .setTitle("앱 숨김 관리 (체크 = 숨김)")
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("확인", (dialog, which) -> {
                    hiddenKeys.clear();
                    for (int i = 0; i < apps.size(); i++) {
                        if (checked[i]) {
                            hiddenKeys.add(apps.get(i).key());
                        }
                    }
                    store.saveHiddenKeys(hiddenKeys);
                    rebuildVisibleApps();
                    clampCurrentPage();
                    renderCurrentPage();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showResetOrderDialog() {
        new AlertDialog.Builder(this)
                .setMessage("앱 순서를 가나다순으로 초기화할까요?")
                .setPositiveButton("초기화", (dialog, which) -> {
                    Collator collator = Collator.getInstance();
                    Collections.sort(allOrdered, (a, b) -> collator.compare(a.label, b.label));
                    saveOrder();
                    rebuildVisibleApps();
                    currentPage = 0;
                    renderCurrentPage();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ---- 아이콘 보관함 ----

    private File iconLibDir() {
        File dir = new File(getFilesDir(), "iconlib");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** 커스텀 아이콘이 지정돼 있으면 그것을, 없으면 원본 아이콘을 돌려준다. */
    Drawable iconFor(AppInfo app) {
        String fileName = store.customIcon(app.key());
        File file = fileName == null ? null : new File(iconLibDir(), fileName);
        return app.icon(this, file);
    }

    private void showIconLibraryDialog(AppInfo app) {
        File[] files = iconLibDir().listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) {
            files = new File[0];
        }
        java.util.Arrays.sort(files);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(app.label + " — 아이콘 보관함")
                .setPositiveButton("이미지 추가", (dialog, which) -> {
                    pendingIconApp = app;
                    pickIconImage();
                })
                .setNeutralButton("원본 아이콘으로", (dialog, which) -> {
                    store.clearCustomIcon(app.key());
                    app.invalidateIcon();
                    renderCurrentPage();
                })
                .setNegativeButton("닫기", null);

        if (files.length == 0) {
            builder.setMessage("보관함이 비어 있습니다.\n'이미지 추가'로 아이콘 이미지를 넣어보세요.")
                    .show();
            return;
        }

        final File[] iconFiles = files;
        float density = getResources().getDisplayMetrics().density;
        int imageSize = (int) (64 * density);
        int cellPadding = (int) (8 * density);

        android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(4);
        int gridPadding = (int) (16 * density);
        grid.setPadding(gridPadding, gridPadding, gridPadding, gridPadding);

        AlertDialog dialog = builder.setView(grid).create();

        grid.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return iconFiles.length;
            }

            @Override
            public Object getItem(int position) {
                return iconFiles[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                File file = iconFiles[position];

                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(android.view.Gravity.CENTER);
                cell.setPadding(cellPadding, cellPadding, cellPadding, cellPadding);

                android.widget.ImageView image = new android.widget.ImageView(MainActivity.this);
                image.setLayoutParams(new LinearLayout.LayoutParams(imageSize, imageSize));
                Bitmap thumb = BitmapFactory.decodeFile(file.getAbsolutePath());
                image.setImageBitmap(thumb);
                image.setOnClickListener(v -> {
                    store.setCustomIcon(app.key(), file.getName());
                    app.invalidateIcon();
                    renderCurrentPage();
                    dialog.dismiss();
                });

                TextView delete = new TextView(MainActivity.this);
                delete.setText("삭제");
                delete.setTextSize(12);
                delete.setTextColor(0xFF000000);
                delete.setGravity(android.view.Gravity.CENTER);
                delete.setPadding(cellPadding, cellPadding / 2, cellPadding, cellPadding / 2);
                delete.setOnClickListener(v -> {
                    // 이 이미지를 쓰던 앱들은 원본 아이콘으로 복귀
                    for (String appKey : store.appKeysUsingIcon(file.getName())) {
                        store.clearCustomIcon(appKey);
                        for (AppInfo a : allOrdered) {
                            if (a.key().equals(appKey)) {
                                a.invalidateIcon();
                            }
                        }
                    }
                    file.delete();
                    renderCurrentPage();
                    dialog.dismiss();
                    showIconLibraryDialog(app);
                });

                cell.addView(image);
                cell.addView(delete);
                return cell;
            }
        });

        dialog.show();
    }

    private void pickIconImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "아이콘 이미지 선택"),
                    REQUEST_PICK_ICON);
        } catch (ActivityNotFoundException e) {
            // 이미지 선택 가능한 앱이 없는 경우
        }
    }

    /** 선택한 이미지를 정사각형 아이콘 크기로 잘라 보관함에 저장한다. */
    private void saveIconToLibrary(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("decode bounds failed");
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= ICON_SIZE
                && bounds.outHeight / (opts.inSampleSize * 2) >= ICON_SIZE) {
            opts.inSampleSize *= 2;
        }
        Bitmap source;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(in, null, opts);
        }
        if (source == null) {
            throw new IOException("decode failed");
        }

        float scale = Math.max(
                (float) ICON_SIZE / source.getWidth(),
                (float) ICON_SIZE / source.getHeight());
        int drawW = Math.round(source.getWidth() * scale);
        int drawH = Math.round(source.getHeight() * scale);

        Bitmap result = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        int left = (ICON_SIZE - drawW) / 2;
        int top = (ICON_SIZE - drawH) / 2;
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new Rect(left, top, left + drawW, top + drawH), paint);
        source.recycle();

        File file = new File(iconLibDir(), "icon_" + System.currentTimeMillis() + ".png");
        try (OutputStream out = new FileOutputStream(file)) {
            result.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        result.recycle();
    }

    // ---- 배경화면 ----

    private File wallpaperFile() {
        return new File(getFilesDir(), "wallpaper.png");
    }

    /** 저장된 배경화면 사본이 있으면 루트 배경으로, 없으면 흰색. */
    private void applyWallpaper() {
        File file = wallpaperFile();
        if (file.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap != null) {
                homeRoot.setBackground(new BitmapDrawable(getResources(), bitmap));
                return;
            }
        }
        homeRoot.setBackgroundColor(0xFFFFFFFF);
    }

    private void pickWallpaper() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "배경화면 선택"),
                    REQUEST_PICK_WALLPAPER);
        } catch (ActivityNotFoundException e) {
            // 이미지 선택 가능한 앱이 없는 경우
        }
    }

    private void removeWallpaper() {
        wallpaperFile().delete();
        applyWallpaper();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WALLPAPER && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            try {
                saveWallpaperCopy(data.getData());
                applyWallpaper();
            } catch (IOException | OutOfMemoryError e) {
                new AlertDialog.Builder(this)
                        .setMessage("이미지를 불러올 수 없습니다.")
                        .setPositiveButton("확인", null)
                        .show();
            }
        }
        if (requestCode == REQUEST_PICK_ICON && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            try {
                saveIconToLibrary(data.getData());
            } catch (IOException | OutOfMemoryError e) {
                new AlertDialog.Builder(this)
                        .setMessage("이미지를 불러올 수 없습니다.")
                        .setPositiveButton("확인", null)
                        .show();
            }
            // 보관함으로 돌아와 방금 넣은 이미지를 바로 고를 수 있게 한다
            if (pendingIconApp != null) {
                showIconLibraryDialog(pendingIconApp);
                pendingIconApp = null;
            }
        }
    }

    /**
     * 선택한 이미지를 화면 크기 사본으로 저장한다.
     * 크기와 무관하게 비율을 유지하며 화면을 꽉 채우고 넘치는 부분은 중앙 크롭(cover).
     */
    private void saveWallpaperCopy(Uri uri) throws IOException {
        int targetW = homeRoot.getWidth();
        int targetH = homeRoot.getHeight();
        if (targetW <= 0 || targetH <= 0) {
            targetW = getResources().getDisplayMetrics().widthPixels;
            targetH = getResources().getDisplayMetrics().heightPixels;
        }

        // 1) 크기만 먼저 읽어서 대략 화면 크기까지만 샘플링 디코딩 (거대 이미지 OOM 방지)
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("decode bounds failed");
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= targetW
                && bounds.outHeight / (opts.inSampleSize * 2) >= targetH) {
            opts.inSampleSize *= 2;
        }
        Bitmap source;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(in, null, opts);
        }
        if (source == null) {
            throw new IOException("decode failed");
        }

        // 2) cover 배율 계산 (작은 이미지는 확대해서라도 꽉 채운다)
        float scale = Math.max(
                (float) targetW / source.getWidth(),
                (float) targetH / source.getHeight());
        int drawW = Math.round(source.getWidth() * scale);
        int drawH = Math.round(source.getHeight() * scale);

        // 3) 화면 크기 캔버스에 흰 배경 + 중앙 배치로 합성 (넘치는 부분은 자연 크롭)
        Bitmap result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(0xFFFFFFFF);
        int left = (targetW - drawW) / 2;
        int top = (targetH - drawH) / 2;
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new Rect(left, top, left + drawW, top + drawH), paint);
        source.recycle();

        try (OutputStream out = new FileOutputStream(wallpaperFile())) {
            result.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        result.recycle();
    }

    // ---- 페이지 ----

    private int pageCount() {
        return Math.max(1, (visibleApps.size() + pageSize - 1) / pageSize);
    }

    private void clampCurrentPage() {
        if (currentPage >= pageCount()) {
            currentPage = pageCount() - 1;
        }
    }

    private void renderCurrentPage() {
        renderer.render(visibleApps, currentPage, movingApp);

        // 페이지가 하나뿐이면 인디케이터를 표시하지 않는다
        if (pageCount() <= 1) {
            pageIndicator.setText("");
            return;
        }
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < pageCount(); i++) {
            if (dots.length() > 0) {
                dots.append("  ");
            }
            dots.append(i == currentPage ? '●' : '○');
        }
        pageIndicator.setText(dots);
    }

    private void nextPage() {
        if (currentPage + 1 < pageCount()) {
            currentPage++;
            renderCurrentPage();
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            renderCurrentPage();
        }
    }

    void launchApp(AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.packageName, app.activityName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            // 실행할 수 없는 앱은 무시한다
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 크레마 팔레트 물리키: 위 = 92 PAGE_UP, 아래 = 93 PAGE_DOWN (실기기 확인)
        // 평소에는 HomeRootLayout의 preIme 가로채기가 먼저 처리하고, 여기는 안전망이다.
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            previousPage();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            nextPage();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 이동 모드는 뒤로가기로 취소, 그 외에는 홈이므로 소비만 한다
            if (movingApp != null) {
                movingApp = null;
                renderCurrentPage();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
