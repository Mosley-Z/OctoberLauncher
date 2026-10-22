package com.shuiyu.octoberrain;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.ArrayList;

/** Debug-only, DUMP-protected renderer fault injection. No document/file/config access. */
public final class ReaderTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            java.lang.reflect.Field field = LauncherRouter.class.getDeclaredField("activity");
            field.setAccessible(true);
            Activity activity = (Activity) field.get(null);
            if (activity == null) return;
            ArrayList<WebView> views = new ArrayList<>();
            collect(activity.getWindow().getDecorView(), views);
            if (views.size() != 1) { Log.i("ReaderTest", "ambiguous WebView count=" + views.size()); return; }
            WebView view = views.get(0);
            if ("com.shuiyu.octoberrain.TEST_CRASH_WEB".equals(intent.getAction())) {
                Log.i("ReaderTest", "controlled renderer crash requested");
                view.loadUrl("chrome://crash");
            } else if ("com.shuiyu.octoberrain.TEST_TERMINATE_WEB".equals(intent.getAction()) && Build.VERSION.SDK_INT >= 29) {
                android.webkit.WebViewRenderProcess process = view.getWebViewRenderProcess();
                Log.i("ReaderTest", "controlled renderer terminate=" + (process != null && process.terminate()));
            }
        } catch (ReflectiveOperationException failure) {
            Log.e("ReaderTest", "test host unavailable: " + failure.getClass().getSimpleName());
        }
    }
    private static void collect(View view, ArrayList<WebView> views) {
        if (view instanceof WebView) { views.add((WebView)view); return; }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), views);
        }
    }
}
