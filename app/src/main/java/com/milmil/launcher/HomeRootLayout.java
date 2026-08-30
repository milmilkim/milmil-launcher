package com.milmil.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * 홈 화면 루트 레이아웃.
 *
 * 1) 터치 모드에서 PAGE_UP/PAGE_DOWN 첫 입력은 프레임워크가 "터치 모드 탈출"에
 *    소비해버리므로(ViewRootImpl.checkForLeavingTouchModeAndConsume), 그보다 먼저
 *    실행되는 dispatchKeyEventPreIme 단계에서 페이지 키를 가로채 전달한다.
 * 2) 좌우 스와이프를 감지해 페이지 전환으로 전달한다. 가로 이동이 충분히 커지면
 *    자식(앱 셀)에게서 터치를 가로채 탭/실행과 충돌하지 않게 한다.
 */
public class HomeRootLayout extends LinearLayout {

    interface PageKeyListener {
        /** 처리했으면 true */
        boolean onPageKey(int keyCode);
    }

    interface SwipeListener {
        /** toNext = 왼쪽으로 쓸어넘김(다음 페이지) */
        void onSwipe(boolean toNext);
    }

    private PageKeyListener pageKeyListener;
    private SwipeListener swipeListener;

    private float downX;
    private float downY;
    private final int touchSlop;
    private final int minSwipeDistance;

    public HomeRootLayout(Context context) {
        this(context, null);
    }

    public HomeRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        minSwipeDistance = (int) (64 * context.getResources().getDisplayMetrics().density);
    }

    void setPageKeyListener(PageKeyListener listener) {
        this.pageKeyListener = listener;
    }

    void setSwipeListener(SwipeListener listener) {
        this.swipeListener = listener;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (pageKeyListener != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_PAGE_UP
                    || event.getKeyCode() == KeyEvent.KEYCODE_PAGE_DOWN)) {
            if (pageKeyListener.onPageKey(event.getKeyCode())) {
                return true;
            }
        }
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                // 가로 제스처가 분명해지면 자식 탭을 취소하고 스와이프로 처리한다
                if (Math.abs(dx) > touchSlop * 2
                        && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                // 가로로 끌기 시작하면 대기 중인 길게 누르기를 취소한다
                if (Math.abs(ev.getX() - downX) > touchSlop * 2) {
                    cancelLongPress();
                }
                break;
            case MotionEvent.ACTION_UP:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (swipeListener != null
                        && Math.abs(dx) > minSwipeDistance
                        && Math.abs(dx) > Math.abs(dy)) {
                    swipeListener.onSwipe(dx < 0);
                    // 눌림 상태와 남은 콜백을 정리해 길게 누르기 오발을 막는다
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                    super.onTouchEvent(ev);
                    return true;
                }
                break;
        }
        return super.onTouchEvent(ev);
    }
}
