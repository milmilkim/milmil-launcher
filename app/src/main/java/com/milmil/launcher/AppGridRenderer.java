package com.milmil.launcher;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * 앱 패널 그리드 렌더러.
 * 셀 뷰는 페이지 크기만큼 한 번만 만들어두고, 페이지 전환 시 내용만 다시 바인딩한다.
 * 이동 모드에서는 빈 셀도 탭 대상으로 보이게 하고, 선택된 앱에 검정 테두리를 그린다.
 */
public class AppGridRenderer {

    private final MainActivity activity;
    private final int pageSize;
    private final View[] cells;
    private final ImageView[] icons;
    private final TextView[] labels;

    AppGridRenderer(MainActivity activity, GridLayout grid, int columns, int rows,
            int iconSizePx) {
        this.activity = activity;
        this.pageSize = columns * rows;
        this.cells = new View[pageSize];
        this.icons = new ImageView[pageSize];
        this.labels = new TextView[pageSize];

        grid.setColumnCount(columns);
        grid.setRowCount(rows);

        LayoutInflater inflater = activity.getLayoutInflater();
        for (int i = 0; i < pageSize; i++) {
            final int cellIndex = i;
            View cell = inflater.inflate(R.layout.item_app, grid, false);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / columns, 1f),
                    GridLayout.spec(i % columns, 1f));
            lp.width = 0;
            lp.height = 0;
            cell.setLayoutParams(lp);
            // 셀이 포커스를 가져가면 물리키 첫 입력이 포커스 이동에 소비되므로 막는다
            cell.setFocusable(false);
            cell.setOnClickListener(v -> this.activity.onCellTapped(cellIndex));
            cell.setOnLongClickListener(v -> this.activity.onCellLongPressed(cellIndex));
            grid.addView(cell);
            cells[i] = cell;
            icons[i] = cell.findViewById(R.id.app_icon);
            labels[i] = cell.findViewById(R.id.app_label);
            // 그리드 셀 크기는 그대로 두고 아이콘 이미지 크기만 조절한다
            android.view.ViewGroup.LayoutParams iconLp = icons[i].getLayoutParams();
            iconLp.width = iconSizePx;
            iconLp.height = iconSizePx;
            icons[i].setLayoutParams(iconLp);

            // 큰 아이콘은 커진 만큼 셀 안쪽 여백을 줄여 앱 이름이 잘리지 않게 한다
            float density = activity.getResources().getDisplayMetrics().density;
            if (iconSizePx > (int) (48 * density)) {
                int padH = (int) (8 * density);
                int padV = (int) (2 * density);
                cell.setPadding(padH, padV, padH, padV);
                LinearLayout.LayoutParams labelLp =
                        (LinearLayout.LayoutParams) labels[i].getLayoutParams();
                labelLp.topMargin = (int) (2 * density);
                labels[i].setLayoutParams(labelLp);
            }
        }
    }

    void render(List<AppInfo> apps, int page, AppInfo moving) {
        int start = page * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int index = start + i;
            if (index < apps.size()) {
                AppInfo app = apps.get(index);
                cells[i].setVisibility(View.VISIBLE);
                cells[i].setTag(app);
                cells[i].setBackgroundResource(app == moving ? R.drawable.cell_selected : 0);
                icons[i].setImageDrawable(activity.iconFor(app));
                labels[i].setText(app.label);
            } else {
                cells[i].setTag(null);
                cells[i].setBackgroundResource(0);
                icons[i].setImageDrawable(null);
                labels[i].setText("");
                // 이동 모드에서는 빈 셀도 "맨 뒤로 이동" 탭 대상으로 살려둔다
                cells[i].setVisibility(moving != null ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }
}
