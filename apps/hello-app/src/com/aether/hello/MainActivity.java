package com.aether.hello;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Material You (M3) 风格界面 — 纯框架 API，无第三方库
 * - 全面屏 edge-to-edge + 手动 insets 适配（targetSdk 35+ 强制）
 * - Android 12+ 自动跟随壁纸动态取色（Monet），低版本用 M3 基准紫
 * - 深色模式自适应
 */
public class MainActivity extends Activity {

    private TextView counterView;
    private int taps = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        int accent = accentColor(night);
        int bg    = night ? 0xFF141218 : 0xFFFEF7FF; // M3 surface
        int onBg  = night ? 0xFFE6E0E9 : 0xFF1D1B20; // M3 on-surface
        int onVar = night ? 0xFFCAC4D0 : 0xFF49454F; // M3 on-surface-variant
        int card  = blend(bg, accent, night ? 0.16f : 0.10f);

        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding((int) (24 * dp), (int) (32 * dp), (int) (24 * dp), (int) (24 * dp));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets s = insets.getInsets(WindowInsets.Type.systemBars());
                top = s.top;
                bottom = s.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding((int) (24 * dp), top + (int) (32 * dp), (int) (24 * dp),
                    bottom + (int) (24 * dp));
            return insets;
        });

        // 顶部小徽标
        TextView chip = new TextView(this);
        chip.setText("AETHER · ON-DEVICE BUILD");
        chip.setTextSize(11f);
        chip.setLetterSpacing(0.15f);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setTextColor(accent);
        chip.setPadding((int) (14 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(20 * dp);
        chipBg.setColor(blend(bg, accent, 0.14f));
        chip.setBackground(chipBg);
        root.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 大标题
        TextView title = new TextView(this);
        title.setText("Hello from\nAether!");
        title.setTextSize(40f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(onBg);
        title.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = (int) (20 * dp);
        root.addView(title, titleLp);

        // 正文
        TextView body = new TextView(this);
        body.setText("这个 APK 完全在手机上构建：\nAlpine 沙箱 · JDK 17 · javac → d8 → MiniAapt → apksigner");
        body.setTextSize(15f);
        body.setLineSpacing(0, 1.3f);
        body.setTextColor(onVar);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = (int) (12 * dp);
        root.addView(body, bodyLp);

        // M3 圆角卡片
        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding((int) (20 * dp), (int) (18 * dp), (int) (20 * dp), (int) (18 * dp));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(28 * dp);
        cardBg.setColor(card);
        cardView.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = (int) (28 * dp);
        root.addView(cardView, cardLp);

        counterView = new TextView(this);
        counterView.setText("👋 还没有点过按钮");
        counterView.setTextSize(16f);
        counterView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        counterView.setTextColor(blend(card, onBg, 0.75f));
        cardView.addView(counterView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // M3 胶囊按钮（涟漪反馈）
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(28 * dp);
        btnBg.setColor(accent);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(28 * dp);
        mask.setColor(Color.WHITE);
        Button btn = new Button(this);
        btn.setText("点我 TAP ME");
        btn.setAllCaps(false);
        btn.setTextSize(15f);
        btn.setLetterSpacing(0.08f);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setStateListAnimator(null);
        btn.setMinHeight(0);
        btn.setMinWidth(0);
        btn.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
        btn.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(0x40, 0xFF, 0xFF, 0xFF)), btnBg, mask));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (56 * dp));
        btnLp.topMargin = (int) (16 * dp);
        btn.setOnClickListener(v -> {
            taps++;
            counterView.setText("🔥 已点击 " + taps + " 次");
        });
        cardView.addView(btn, btnLp);

        // 底部注脚
        TextView foot = new TextView(this);
        foot.setText("minSdk 28 (Android 9) · targetSdk 36 (Android 16)\n"
                + "设备 API " + Build.VERSION.SDK_INT
                + (Build.VERSION.SDK_INT >= 31 ? " · Monet 壁纸动态取色 ✓" : " · M3 基准色"));
        foot.setTextSize(12f);
        foot.setTextColor(blend(bg, onBg, 0.45f));
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = (int) (24 * dp);
        root.addView(foot, footLp);

        setContentView(root);
        // 必须在 setContentView 之后：DecorView 此时才创建，
        // 过早调 getInsetsController() 会 NPE（真机已踩坑验证）
        edgeToEdge(night);
    }

    /** 简易 ARGB 颜色混合（替代 androidx ColorUtils.blendARGB） */
    private static int blend(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int a = (int) (Color.alpha(c1) * inv + Color.alpha(c2) * ratio);
        int r = (int) (Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.argb(a, r, g, b);
    }

    /** Android 12+ 读取框架 Monet 动态色，否则回退 M3 基准紫 #6750A4 */
    private int accentColor(boolean night) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                int id = getResources().getIdentifier(
                        "system_accent1_" + (night ? "200" : "600"), "color", "android");
                if (id != 0) return getResources().getColor(id, getTheme());
            } catch (Exception ignored) {
            }
        }
        return 0xFF6750A4;
    }

    /** 全面屏沉浸式：透明系统栏 + 图标颜色自适应 */
    private void edgeToEdge(boolean night) {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : light, light);
            }
        } else {
            View decor = w.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (!night) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }
}
