package com.milmil.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;

/** 홈에 표시되는 앱 하나의 정보. 아이콘은 처음 필요할 때 로딩해서 캐시한다. */
public class AppInfo {

    final String label;
    final String packageName;
    final String activityName;
    private Drawable icon;

    AppInfo(String label, String packageName, String activityName) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
    }

    /** 저장용 고유 키 */
    String key() {
        return packageName + "/" + activityName;
    }

    /** 커스텀 아이콘 파일이 있으면 그것을, 없으면 원본 앱 아이콘을 쓴다. */
    Drawable icon(Context context, File customIconFile) {
        if (icon == null) {
            if (customIconFile != null && customIconFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(customIconFile.getAbsolutePath());
                if (bitmap != null) {
                    icon = new BitmapDrawable(context.getResources(), bitmap);
                }
            }
            if (icon == null) {
                try {
                    icon = context.getPackageManager().getActivityIcon(
                            new ComponentName(packageName, activityName));
                } catch (PackageManager.NameNotFoundException e) {
                    icon = context.getPackageManager().getDefaultActivityIcon();
                }
            }
        }
        return icon;
    }

    /** 커스텀 아이콘 변경/해제 시 캐시를 비운다. */
    void invalidateIcon() {
        icon = null;
    }
}
