package com.aether.fluiddiary;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 出入量日记 — 为颅咽管瘤术后垂体功能下降、尿崩症患者设计
 * 记录每日饮水量、尿量、用药情况，支持趋势查看与数据导出
 * 纯框架 API 实现（Monet 动态取色 + M3 风格），数据存 SharedPreferences，完全离线
 */
public class MainActivity extends Activity {

    // ---- 主题色 ----
    private int bg, onBg, onVar, accent, accent2, danger;
    private float dp;
    private SharedPreferences prefs;
    private FrameLayout container;
    private final TextView[] navLabel = new TextView[4];
    private final LinearLayout[] navItems = new LinearLayout[4];
    private int currentTab = 0;
    private int windowDays = 7;

    private final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private final SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat sdfLong = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);

    private static final String[] DEFAULT_MEDS = {
            "去氨加压素（弥凝）", "氢化可的松", "左甲状腺素（优甲乐）", "生长激素"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("fluid", MODE_PRIVATE);
        if (!prefs.contains("meds")) {
            JSONArray a = new JSONArray();
            for (String m : DEFAULT_MEDS) a.put(m);
            prefs.edit().putString("meds", a.toString()).apply();
        }

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        accent = accentColor(night, 1);
        accent2 = accentColor(night, 2);
        bg = night ? 0xFF141218 : 0xFFFEF7FF;
        onBg = night ? 0xFFE6E0E9 : 0xFF1D1B20;
        onVar = night ? 0xFFCAC4D0 : 0xFF49454F;
        danger = night ? 0xFFF2B8B5 : 0xFFB3261E;
        dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
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
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildNavBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (62 * dp)));

        setContentView(root);
        edgeToEdge(night);
        select(0);
    }

    // ==================== 底部导航 ====================

    private View buildNavBar() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable nb = new GradientDrawable();
        nb.setColor(blend(bg, onBg, 0.045f));
        nav.setBackground(nb);
        String[] tabs = {"今日", "记录", "趋势", "设置"};
        for (int i = 0; i < 4; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER);
            TextView lb = text(tabs[i], 13, onVar, true);
            lb.setGravity(Gravity.CENTER);
            navLabel[i] = lb;
            item.addView(lb);
            navItems[i] = item;
            final int idx = i;
            item.setOnClickListener(v -> select(idx));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -1, 1f);
            ilp.setMargins((int) (5 * dp), (int) (9 * dp), (int) (5 * dp), (int) (9 * dp));
            nav.addView(item, ilp);
        }
        return nav;
    }

    private void select(int tab) {
        currentTab = tab;
        for (int i = 0; i < 4; i++) {
            boolean on = i == tab;
            navLabel[i].setTextColor(on ? accent : onVar);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(22 * dp);
            g.setColor(on ? blend(accent, bg, 0.86f) : Color.TRANSPARENT);
            navItems[i].setBackground(g);
        }
        container.removeAllViews();
        View page = tab == 0 ? buildToday() : tab == 1 ? buildHistory()
                : tab == 2 ? buildTrends() : buildSettings();
        container.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // ==================== 今日页 ====================

    private View buildToday() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = col(20);
        sv.addView(root, new FrameLayout.LayoutParams(-1, -2));
        String date = today();
        int[] t = dayTotals(date);
        int diff = t[0] - t[1];

        root.addView(text(sdfLong.format(new Date()), 24, onBg, true));
        TextView sub = text("出入量 · 用药记录", 13, onVar, false);
        sub.setPadding(0, (int) (3 * dp), 0, 0);
        root.addView(sub);

        // ---- 总览卡 ----
        LinearLayout ov = card(blend(bg, accent, 0.10f));
        root.addView(ov, cardLp());
        LinearLayout ovRow = new LinearLayout(this);
        ovRow.setOrientation(LinearLayout.HORIZONTAL);
        ovRow.setGravity(Gravity.CENTER);
        ovRow.addView(statCol("饮水", fmt(t[0]) + " ml", onBg), w1());
        ovRow.addView(vDiv(), new LinearLayout.LayoutParams((int) (1 * dp), (int) (30 * dp)));
        ovRow.addView(statCol("尿量", fmt(t[1]) + " ml", onBg), w1());
        ovRow.addView(vDiv(), new LinearLayout.LayoutParams((int) (1 * dp), (int) (30 * dp)));
        ovRow.addView(statCol("净差", (diff > 0 ? "+" : "") + fmt(diff) + " ml",
                Math.abs(diff) >= 1500 ? danger : onBg), w1());
        ov.addView(ovRow);
        TextView note = text("净差 = 饮水 − 尿量；连续多日明显偏高或偏低，复诊时请告知医生", 11, onVar, false);
        note.setPadding(0, (int) (10 * dp), 0, 0);
        ov.addView(note);

        // ---- 饮水记账 ----
        LinearLayout cw = card(blend(bg, onBg, 0.045f));
        root.addView(cw, cardLp());
        cw.addView(sectionTitle("饮水记账", accent));
        cw.addView(chips(0), rowLp());

        // ---- 尿量记账 ----
        LinearLayout cu = card(blend(bg, onBg, 0.045f));
        root.addView(cu, cardLp());
        cu.addView(sectionTitle("尿量记账", accent2));
        cu.addView(chips(1), rowLp());

        // ---- 用药打卡 ----
        LinearLayout cm = card(blend(bg, accent2, 0.10f));
        root.addView(cm, cardLp());
        cm.addView(sectionTitle("用药打卡", accent2));
        TextView medHint = text("点按药物即记录当前时间服药", 11, onVar, false);
        medHint.setPadding(0, (int) (3 * dp), 0, (int) (8 * dp));
        cm.addView(medHint);
        List<String> meds = loadMeds();
        if (meds.isEmpty()) cm.addView(text("还没有药物，去「设置」里添加", 13, onVar, false));
        for (String m : meds) {
            Button b = pill("+  " + m, blend(accent2, bg, 0.82f), onBg);
            cm.addView(b, pillLp());
            b.setOnClickListener(v -> {
                try {
                    JSONObject d = loadDay(date);
                    JSONArray mArr = d.optJSONArray("m");
                    if (mArr == null) {
                        mArr = new JSONArray();
                        d.put("m", mArr);
                    }
                    JSONArray item = new JSONArray();
                    item.put(sdfTime.format(new Date())).put(m);
                    mArr.put(item);
                    saveDay(date, d);
                    Toast.makeText(this, "已记录 " + m, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }
                select(currentTab);
            });
        }
        JSONArray mArr = loadDay(date).optJSONArray("m");
        if (mArr != null && mArr.length() > 0) {
            TextView taken = text("今日已服（点按撤销）", 12, onVar, true);
            taken.setPadding(0, (int) (10 * dp), 0, (int) (4 * dp));
            cm.addView(taken);
            for (int i = 0; i < mArr.length(); i++) {
                final int idx = i;
                try {
                    JSONArray it = mArr.getJSONArray(i);
                    TextView row = text("✓  " + it.getString(0) + "  ·  " + it.getString(1), 14, onBg, true);
                    row.setPadding(0, (int) (6 * dp), 0, 0);
                    row.setOnClickListener(v -> confirm("撤销这条用药记录？\n\n" + "时间 " + safe(it, 0)
                            + " · " + safe(it, 1), () -> removeAt(date, idx, true)));
                    cm.addView(row);
                } catch (Exception ignored) {
                }
            }
        }

        // ---- 今日明细 ----
        LinearLayout cd = card(blend(bg, onBg, 0.045f));
        root.addView(cd, cardLp());
        cd.addView(sectionTitle("今日明细 · 点按删除", blend(bg, onBg, 0.4f)));
        // 合并出入量 + 用药，按时间倒序（最新在前）
        List<Object[]> all = new ArrayList<>();
        JSONArray eArr = loadDay(date).optJSONArray("e");
        if (eArr != null) {
            for (int i = 0; i < eArr.length(); i++) {
                try {
                    JSONArray it = eArr.getJSONArray(i);
                    boolean isWater = "w".equals(it.getString(1));
                    all.add(new Object[]{it.getString(0),
                            (isWater ? "饮水 +" : "尿量 −") + fmt(it.getInt(2)) + " ml",
                            false, i,
                            (isWater ? "饮水 " : "尿量 ") + fmt(it.getInt(2)) + " ml"});
                } catch (Exception ignored) {
                }
            }
        }
        JSONArray mAll = loadDay(date).optJSONArray("m");
        if (mAll != null) {
            for (int i = 0; i < mAll.length(); i++) {
                try {
                    JSONArray it = mAll.getJSONArray(i);
                    all.add(new Object[]{it.getString(0), "服药 · " + it.getString(1),
                            true, i, it.getString(1)});
                } catch (Exception ignored) {
                }
            }
        }
        all.sort((a, b) -> ((String) b[0]).compareTo((String) a[0]));
        if (all.isEmpty()) {
            TextView em = text("今天还没有记录", 13, onVar, false);
            em.setPadding(0, (int) (8 * dp), 0, 0);
            cd.addView(em);
        }
        for (Object[] r : all) {
            final boolean isMed = (Boolean) r[2];
            final int idx = (Integer) r[3];
            TextView row = text(r[0] + "  ·  " + r[1], 14, onBg, false);
            row.setPadding(0, (int) (8 * dp), 0, 0);
            if (isMed) {
                row.setOnClickListener(v -> confirm("撤销这条用药记录？\n\n"
                        + r[0] + "  " + r[4], () -> removeAt(date, idx, true)));
            } else {
                row.setOnClickListener(v -> confirm("删除这条记录？\n\n"
                        + r[0] + "  " + r[4], () -> removeAt(date, idx, false)));
            }
            cd.addView(row);
        }
        return sv;
    }

    private View chips(int kind) {
        int[] vals = kind == 0 ? new int[]{100, 250, 500, 1000} : new int[]{100, 200, 300, 500};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (12 * dp), 0, 0);
        for (final int v : vals) {
            TextView c = chipBtn("+" + v, () -> addWaterUrine(kind == 0 ? "w" : "u", v));
            row.addView(c, chipLp());
        }
        TextView custom = chipBtn("自定义", () -> customDialog(kind));
        row.addView(custom, chipLp());
        return row;
    }

    private void addWaterUrine(String kind, int ml) {
        String date = today();
        try {
            JSONObject d = loadDay(date);
            JSONArray e = d.optJSONArray("e");
            if (e == null) {
                e = new JSONArray();
                d.put("e", e);
            }
            JSONArray item = new JSONArray();
            item.put(sdfTime.format(new Date())).put(kind).put(ml);
            e.put(item);
            saveDay(date, d);
        } catch (Exception ignored) {
        }
        select(currentTab);
    }

    private void customDialog(int kind) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("毫升（ml）");
        input.setText("200");
        new AlertDialog.Builder(this)
                .setTitle(kind == 0 ? "自定义饮水量" : "自定义尿量")
                .setView(input)
                .setPositiveButton("记录", (dlg, w) -> {
                    try {
                        int ml = Integer.parseInt(input.getText().toString().trim());
                        if (ml > 0 && ml <= 20000) addWaterUrine(kind == 0 ? "w" : "u", ml);
                        else Toast.makeText(this, "请输入 1–20000 之间的数值", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 记录页 ====================

    private View buildHistory() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = col(20);
        sv.addView(root, new FrameLayout.LayoutParams(-1, -2));
        root.addView(text("最近 14 天", 24, onBg, true));
        TextView hint = text("点按某天查看明细", 13, onVar, false);
        hint.setPadding(0, (int) (3 * dp), 0, 0);
        root.addView(hint);

        LinearLayout card = card(blend(bg, onBg, 0.045f));
        root.addView(card, cardLp());
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat mf = new SimpleDateFormat("M/d", Locale.CHINA);
        String[] heads = {"日期", "饮水", "尿量", "净差", "用药"};
        card.addView(tableRow(heads, 11, onVar, false));
        for (int i = 13; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            String date = sdfDate.format(c.getTime());
            int[] t = dayTotals(date);
            int n = medCount(date);
            final String fDate = date;
            boolean dim = t[0] + t[1] + n == 0;
            String[] cells = {mf.format(c.getTime()), fmt(t[0]), fmt(t[1]),
                    (t[0] - t[1] > 0 ? "+" : "") + fmt(t[0] - t[1]), String.valueOf(n)};
            LinearLayout row = tableRow(cells, dim ? 13 : 13.5f, dim ? onVar : onBg, !dim);
            if (!dim && Math.abs(t[0] - t[1]) >= 1500)
                ((TextView) row.getChildAt(3)).setTextColor(danger);
            row.setOnClickListener(v -> dayDialog(fDate));
            card.addView(divider(), new LinearLayout.LayoutParams(-1, (int) (1 * dp)));
            card.addView(row);
        }
        return sv;
    }

    private void dayDialog(String date) {
        int[] t = dayTotals(date);
        StringBuilder sb = new StringBuilder();
        sb.append("饮水 ").append(fmt(t[0])).append(" ml · 尿量 ").append(fmt(t[1])).append(" ml\n\n");
        JSONArray e = loadDay(date).optJSONArray("e");
        JSONArray m = loadDay(date).optJSONArray("m");
        try {
            if (e != null) for (int i = 0; i < e.length(); i++) {
                JSONArray it = e.getJSONArray(i);
                sb.append(it.getString(0)).append("  ")
                        .append("w".equals(it.getString(1)) ? "饮水 " : "尿量 ")
                        .append(it.getInt(2)).append(" ml\n");
            }
            if (m != null && m.length() > 0) {
                sb.append("\n用药：\n");
                for (int i = 0; i < m.length(); i++) {
                    JSONArray it = m.getJSONArray(i);
                    sb.append(it.getString(0)).append("  ").append(it.getString(1)).append("\n");
                }
            }
        } catch (Exception ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle(date)
                .setMessage(sb.length() > 0 ? sb.toString() : "当天没有记录")
                .setPositiveButton("关闭", null)
                .setNeutralButton("删除该日数据", (d, w) -> confirm("确定删除 " + date + " 的全部数据？",
                        () -> {
                            prefs.edit().remove("d" + date).apply();
                            select(currentTab);
                        }))
                .show();
    }

    // ==================== 趋势页 ====================

    private View buildTrends() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = col(20);
        sv.addView(root, new FrameLayout.LayoutParams(-1, -2));
        root.addView(text("趋势", 24, onBg, true));

        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(0, (int) (12 * dp), 0, 0);
        int[] wins = {7, 14, 30};
        for (final int wv : wins) {
            TextView c = chipBtn(wv + " 天", () -> {
                windowDays = wv;
                select(2);
            });
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(22 * dp);
            g.setColor(wv == windowDays ? accent : blend(bg, accent, 0.14f));
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(22 * dp);
            mask.setColor(Color.WHITE);
            c.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(0x25, 0, 0, 0)), g, mask));
            c.setTextColor(wv == windowDays ? Color.WHITE : accent);
            tabsRow.addView(c, chipLp());
        }
        root.addView(tabsRow);

        // 数据收集
        List<String> dates = new ArrayList<>();
        List<Integer> ws = new ArrayList<>(), us = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        long sumW = 0, sumU = 0;
        int maxU = -1;
        String maxUDate = "";
        for (int i = windowDays - 1; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            String date = sdfDate.format(c.getTime());
            int[] t = dayTotals(date);
            dates.add(date);
            ws.add(t[0]);
            us.add(t[1]);
            sumW += t[0];
            sumU += t[1];
            if (t[1] > maxU) {
                maxU = t[1];
                maxUDate = date.substring(5).replace("-", "/");
            }
        }

        ChartView chart = new ChartView(this);
        chart.setData(dates, ws, us);
        LinearLayout chartCard = card(blend(bg, onBg, 0.045f));
        root.addView(chartCard, cardLp());
        chartCard.addView(sectionTitle(windowDays + " 天出入量", accent));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (300 * dp));
        clp.topMargin = (int) (10 * dp);
        chartCard.addView(chart, clp);

        // 统计
        long avgW = sumW / windowDays, avgU = sumU / windowDays;
        LinearLayout g1 = new LinearLayout(this);
        g1.setOrientation(LinearLayout.HORIZONTAL);
        g1.addView(miniStat("日均饮水", fmt(avgW) + " ml"), w1());
        g1.addView(miniStat("日均尿量", fmt(avgU) + " ml"), w1());
        root.addView(g1, cardLp());
        LinearLayout g2 = new LinearLayout(this);
        g2.setOrientation(LinearLayout.HORIZONTAL);
        g2.addView(miniStat("日均净差", (avgW - avgU > 0 ? "+" : "") + fmt(avgW - avgU) + " ml"), w1());
        g2.addView(miniStat(maxU >= 0 ? "最高尿量 · " + maxUDate : "最高尿量", maxU >= 0 ? fmt(maxU) + " ml" : "—"), w1());
        root.addView(g2, rowLp());

        // 导出
        Button export = pill("复制 30 天数据（可粘贴给医生）", accent, Color.WHITE);
        root.addView(export, pillLp());
        export.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("出入量数据", exportText()));
            Toast.makeText(this, "已复制，可粘贴到微信/备忘录发给医生", Toast.LENGTH_LONG).show();
        });
        return sv;
    }

    private String exportText() {
        StringBuilder sb = new StringBuilder("出入量记录（近30天）\n日期\t饮水(ml)\t尿量(ml)\t净差(ml)\t用药(次)\n");
        Calendar cal = Calendar.getInstance();
        for (int i = 29; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            String date = sdfDate.format(c.getTime());
            int[] t = dayTotals(date);
            sb.append(date.substring(5).replace("-", "/")).append('\t')
                    .append(t[0]).append('\t').append(t[1]).append('\t')
                    .append(t[0] - t[1]).append('\t').append(medCount(date)).append('\n');
        }
        return sb.toString();
    }

    // ==================== 设置页 ====================

    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = col(20);
        sv.addView(root, new FrameLayout.LayoutParams(-1, -2));
        root.addView(text("设置", 24, onBg, true));

        LinearLayout cm = card(blend(bg, onBg, 0.045f));
        root.addView(cm, cardLp());
        cm.addView(sectionTitle("用药名单", accent));
        TextView h = text("点按药物名可删除", 11, onVar, false);
        h.setPadding(0, (int) (3 * dp), 0, (int) (8 * dp));
        cm.addView(h);
        List<String> meds = loadMeds();
        if (meds.isEmpty()) cm.addView(text("（空）", 13, onVar, false));
        for (int i = 0; i < meds.size(); i++) {
            final int idx = i;
            TextView row = text("•  " + meds.get(i) + "   ✕", 15, onBg, true);
            row.setPadding(0, (int) (8 * dp), 0, 0);
            row.setOnClickListener(v -> confirm("删除药物「" + meds.get(idx) + "」？\n已记录的历史不受影响。",
                    () -> {
                        List<String> ms = loadMeds();
                        if (idx < ms.size()) {
                            ms.remove(idx);
                            saveMeds(ms);
                        }
                        select(3);
                    }));
            cm.addView(row);
        }
        Button add = pill("+  添加药物", blend(accent, bg, 0.85f), onBg);
        cm.addView(add, pillLp());
        add.setOnClickListener(v -> {
            final EditText input = new EditText(this);
            input.setHint("药物名称，如 去氨加压素（弥凝）");
            new AlertDialog.Builder(this)
                    .setTitle("添加药物")
                    .setView(input)
                    .setPositiveButton("添加", (d, w) -> {
                        String s = input.getText().toString().trim();
                        if (!s.isEmpty()) {
                            List<String> ms = loadMeds();
                            ms.add(s);
                            saveMeds(ms);
                            select(3);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        LinearLayout about = card(blend(bg, onBg, 0.045f));
        root.addView(about, cardLp());
        about.addView(sectionTitle("关于", blend(bg, onBg, 0.35f)));
        TextView info = text("出入量日记 v1.1\n"
                + "为颅咽管瘤术后垂体功能下降、尿崩症患者设计的出入量记录工具。\n\n"
                + "重要：本应用仅为个人记录工具，不构成任何医疗建议。"
                + "饮水量、尿量与用药请以医生指导为准，去氨加压素等药物剂量调整务必遵医嘱。\n\n"
                + "完全离线 · 数据仅保存在本机\n"
                + "Built on-device: Alpine aarch64 + JDK 17\njavac → d8 → MiniAapt → apksigner",
                12, onVar, false);
        info.setLineSpacing(0, 1.25f);
        info.setPadding(0, (int) (8 * dp), 0, 0);
        about.addView(info);
        return sv;
    }

    // ==================== 图表 ====================

    private class ChartView extends View {
        private final Paint pWater = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pUrine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<String> dates = new ArrayList<>();
        private List<Integer> ws = new ArrayList<>(), us = new ArrayList<>();

        ChartView(Context c) {
            super(c);
            pWater.setColor(accent);
            pUrine.setColor(accent2);
            pText.setColor(onVar);
            pText.setTextSize(10.5f * dp);
            pGrid.setColor(blend(bg, onBg, 0.10f));
            pGrid.setStrokeWidth(Math.max(1f, 0.8f * dp));
        }

        void setData(List<String> d, List<Integer> w, List<Integer> u) {
            dates = d;
            ws = w;
            us = u;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int n = dates.size();
            if (n == 0) return;
            float W = getWidth(), H = getHeight();
            float leftPad = 48 * dp, rightPad = 8 * dp, topPad = 26 * dp, botPad = 20 * dp;
            float chW = W - leftPad - rightPad, chH = H - topPad - botPad;
            int maxV = 500;
            for (int v : ws) maxV = Math.max(maxV, v);
            for (int v : us) maxV = Math.max(maxV, v);
            maxV = (int) (Math.ceil(maxV / 1000f) * 1000f);

            // 网格与纵轴
            for (int g = 0; g <= 3; g++) {
                float gy = topPad + chH * g / 3f;
                cv.drawLine(leftPad, gy, W - rightPad, gy, pGrid);
                int val = (int) (maxV * (3 - g) / 3f);
                cv.drawText(fmtVol(val), 4 * dp, gy + 4 * dp, pText);
            }

            // 图例
            float lx = leftPad;
            cv.drawRect(lx, 8 * dp, lx + 9 * dp, 16 * dp, pWater);
            cv.drawText("饮水", lx + 13 * dp, 15 * dp, pText);
            lx += 13 * dp + pText.measureText("饮水") + 12 * dp;
            cv.drawRect(lx, 8 * dp, lx + 9 * dp, 16 * dp, pUrine);
            cv.drawText("尿量", lx + 13 * dp, 15 * dp, pText);

            // 柱
            float slot = chW / n;
            float barW = Math.min(slot * 0.30f, 13 * dp);
            float gap = Math.min(3 * dp, barW * 0.35f);
            int step = n <= 7 ? 1 : (n <= 14 ? 2 : 5);
            RectF r = new RectF();
            for (int i = 0; i < n; i++) {
                float cx = leftPad + slot * i + slot / 2f;
                bar(cv, r, cx - barW - gap / 2f, barW, topPad, chH, ws.get(i), maxV, pWater);
                bar(cv, r, cx + gap / 2f, barW, topPad, chH, us.get(i), maxV, pUrine);
                if (i % step == 0) {
                    String lbl = dates.get(i).substring(5).replace("-", "/");
                    cv.drawText(lbl, cx - pText.measureText(lbl) / 2f, H - 5 * dp, pText);
                }
            }
        }

        private void bar(Canvas cv, RectF r, float x, float bw, float top, float chH, int v, int maxV, Paint p) {
            float bh = Math.max(chH * v / (float) maxV, v > 0 ? 2 * dp : 0f);
            r.set(x, top + chH - bh, x + bw, top + chH);
            cv.drawRoundRect(r, bw / 2f, bw / 2f, p);
        }

        private String fmtVol(int v) {
            return v >= 1000 ? String.format(Locale.CHINA, "%.1fL", v / 1000f) : String.valueOf(v);
        }
    }

    // ==================== 数据 ====================

    private String today() {
        return sdfDate.format(new Date());
    }

    private JSONObject loadDay(String date) {
        try {
            return new JSONObject(prefs.getString("d" + date, "{\"e\":[],\"m\":[]}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void saveDay(String date, JSONObject d) {
        prefs.edit().putString("d" + date, d.toString()).apply();
    }

    private int[] dayTotals(String date) {
        int w = 0, u = 0;
        try {
            JSONArray e = loadDay(date).optJSONArray("e");
            if (e != null) for (int i = 0; i < e.length(); i++) {
                JSONArray it = e.getJSONArray(i);
                if ("w".equals(it.getString(1))) w += it.getInt(2);
                else u += it.getInt(2);
            }
        } catch (Exception ignored) {
        }
        return new int[]{w, u};
    }

    private int medCount(String date) {
        JSONArray m = loadDay(date).optJSONArray("m");
        return m == null ? 0 : m.length();
    }

    private void removeAt(String date, int idx, boolean med) {
        try {
            JSONObject d = loadDay(date);
            JSONArray arr = d.optJSONArray(med ? "m" : "e");
            if (arr != null) {
                JSONArray out = new JSONArray();
                for (int i = 0; i < arr.length(); i++) if (i != idx) out.put(arr.get(i));
                d.put(med ? "m" : "e", out);
                saveDay(date, d);
            }
        } catch (Exception ignored) {
        }
        select(currentTab);
    }

    private List<String> loadMeds() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("meds", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveMeds(List<String> meds) {
        JSONArray a = new JSONArray();
        for (String m : meds) a.put(m);
        prefs.edit().putString("meds", a.toString()).apply();
    }

    private String safe(JSONArray a, int i) {
        try {
            return a.get(i).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void confirm(String msg, Runnable action) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton("确定", (d, w) -> action.run())
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== UI 构建 ====================

    private TextView text(String s, float sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(medium ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        return t;
    }

    /** 千分位数字 */
    private String fmt(long v) {
        return String.format(Locale.CHINA, "%,d", v);
    }

    /** 节标题：色点 + 文字（替代 emoji） */
    private View sectionTitle(String s, int dotColor) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(dotColor);
        dot.setBackground(dg);
        h.addView(dot, new LinearLayout.LayoutParams((int) (7 * dp), (int) (7 * dp)));
        TextView t = text(s, 15.5f, onBg, true);
        t.setPadding((int) (8 * dp), 0, 0, 0);
        h.addView(t);
        return h;
    }

    /** 竖向细分隔线 */
    private View vDiv() {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(blend(bg, onBg, 0.15f));
        v.setBackground(g);
        return v;
    }

    /** 横向细分隔线 */
    private View divider() {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(blend(bg, onBg, 0.08f));
        v.setBackground(g);
        return v;
    }

    /** 记录页表格行：日期左对齐，其余右对齐 */
    private LinearLayout tableRow(String[] cells, float sp, int color, boolean medium) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (11 * dp), 0, (int) (11 * dp));
        float[] weights = {1.5f, 1.1f, 1.1f, 1.2f, 0.9f};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = text(cells[i], sp, color, medium);
            tv.setGravity(i == 0 ? Gravity.LEFT : Gravity.RIGHT);
            row.addView(tv, new LinearLayout.LayoutParams(0, -2, weights[i]));
        }
        return row;
    }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(24 * dp);
        g.setColor(color);
        c.setBackground(g);
        c.setPadding((int) (18 * dp), (int) (16 * dp), (int) (18 * dp), (int) (16 * dp));
        return c;
    }

    private LinearLayout col(int padDp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding((int) (padDp * dp), (int) (16 * dp), (int) (padDp * dp), (int) (24 * dp));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (16 * dp);
        return lp;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (10 * dp);
        return lp;
    }

    private LinearLayout.LayoutParams pillLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (48 * dp));
        lp.topMargin = (int) (8 * dp);
        return lp;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f);
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        return lp;
    }

    private LinearLayout.LayoutParams w1() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        return lp;
    }

    /** 单列统计（小标签在上 + 大数值在下） */
    private View statCol(String label, String value, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        TextView l = text(label, 11, onVar, false);
        l.setGravity(Gravity.CENTER);
        TextView v = text(value, 20, color, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, (int) (3 * dp), 0, 0);
        c.addView(l);
        c.addView(v);
        return c;
    }

    private View miniStat(String label, String value) {
        LinearLayout c = card(blend(bg, onBg, 0.045f));
        TextView l = text(label, 11, onVar, false);
        l.setGravity(Gravity.CENTER);
        TextView v = text(value, 17, onBg, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, (int) (3 * dp), 0, 0);
        c.addView(l);
        c.addView(v);
        return c;
    }

    private TextView chipBtn(String s, Runnable r) {
        TextView t = text(s, 14, accent, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(22 * dp);
        g.setColor(blend(bg, accent, 0.14f));
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(22 * dp);
        mask.setColor(Color.WHITE);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(0x25, 0, 0, 0)), g, mask));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private Button pill(String s, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14.5f);
        b.setTextColor(textColor);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(24 * dp);
        g.setColor(bgColor);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(24 * dp);
        mask.setColor(Color.WHITE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(0x30, 0, 0, 0)), g, mask));
        return b;
    }

    // ==================== 主题 ====================

    /** Android 12+ 读取框架 Monet 动态色，否则回退 M3 基准色 */
    private int accentColor(boolean night, int which) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                int id = getResources().getIdentifier(
                        "system_accent" + which + "_" + (night ? "200" : "600"), "color", "android");
                if (id != 0) return getResources().getColor(id, getTheme());
            } catch (Exception ignored) {
            }
        }
        return which == 1 ? 0xFF6750A4 : 0xFF7D5260;
    }

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

    private static int blend(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int a = (int) (Color.alpha(c1) * inv + Color.alpha(c2) * ratio);
        int r = (int) (Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.argb(a, r, g, b);
    }
}
