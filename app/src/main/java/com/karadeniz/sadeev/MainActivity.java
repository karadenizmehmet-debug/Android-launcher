/*
 * Sade Ev — Android TV için sade ana ekran (launcher)
 * Mehmet Karadeniz, 2026-2027
 */
package com.karadeniz.sadeev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String PREFS = "sadeev";
    private static final String KEY_FAVS = "favs";
    private static final String KEY_NIGHT = "night";
    private static final String KEY_INIT = "init";
    private static final String TAG_ADD = "__add__";
    private static final int FIRST_RUN_APP_COUNT = 10;

    // Kutucuk boyutu (16:9, TV banner oranı)
    private static final int TILE_W = 192;
    private static final int TILE_H = 108;
    private static final int TILE_RADIUS = 14;

    private static final Locale TR = new Locale("tr", "TR");

    private SharedPreferences prefs;
    private PackageManager pm;
    private boolean night;

    private ImageView wallpaper;
    private LinearLayout row;
    private HorizontalScrollView rowScroll;
    private TextView focusedName;
    private TextView clockView;
    private LinearLayout headerPill;
    private ImageView themeIcon;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buildRow(false);
        }
    };

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        pm = getPackageManager();
        night = prefs.getBoolean(KEY_NIGHT, true);

        if (!prefs.getBoolean(KEY_INIT, false)) {
            seedFavorites();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        buildRow(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        // Ana ekranda geri tuşu yalnızca ilk kutucuğa döner.
        focusFirstTile();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusFirstTile();
    }

    // ------------------------------------------------------------------ arayüz

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Tam ekran duvar kâğıdı
        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Sol üst: ad + sürüm (sade, küçük)
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.BOTTOM);
        TextView name = shadowText("Sade Ev", 20, true);
        TextView ver = shadowText("  v" + versionName(), 13, false);
        ver.setAlpha(0.8f);
        brand.addView(name);
        brand.addView(ver);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        blp.setMargins(dp(48), dp(34), 0, 0);
        root.addView(brand, blp);

        // Sağ üst: yarı saydam hap — gece/gündüz, paylaş, ayarlar, saat
        headerPill = new LinearLayout(this);
        headerPill.setOrientation(LinearLayout.HORIZONTAL);
        headerPill.setGravity(Gravity.CENTER_VERTICAL);
        headerPill.setPadding(dp(6), dp(6), dp(18), dp(6));

        themeIcon = new ImageView(this);
        headerPill.addView(roundButton(themeIcon, night ? R.drawable.ic_sun : R.drawable.ic_moon,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        night = !night;
                        prefs.edit().putBoolean(KEY_NIGHT, night).apply();
                        applyTheme();
                    }
                }));
        headerPill.addView(roundButton(new ImageView(this), R.drawable.ic_share,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        shareApp();
                    }
                }));
        headerPill.addView(roundButton(new ImageView(this), R.drawable.ic_settings,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openSettings();
                    }
                }));

        clockView = shadowText("", 26, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(12);
        headerPill.addView(clockView, clp);

        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        hlp.setMargins(0, dp(24), dp(48), 0);
        root.addView(headerPill, hlp);

        // Alt: odaklı uygulamanın adı + tek sıra uygulama kutucukları
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);

        focusedName = shadowText("", 16, true);
        focusedName.setPadding(dp(56), 0, 0, dp(2));
        bottom.addView(focusedName);

        rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipChildren(false);
        rowScroll.setClipToPadding(false);
        rowScroll.setFillViewport(false);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Büyüyen odak kutucuğu kırpılmasın diye dikey boşluk.
        row.setPadding(dp(48), dp(14), dp(48), dp(14));
        rowScroll.addView(row);
        bottom.addView(rowScroll);

        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        rlp.bottomMargin = dp(44);
        root.addView(bottom, rlp);

        // Telif (çok küçük, sağ alt)
        TextView footer = shadowText("Mehmet Karadeniz, 2026-2027", 10, false);
        footer.setAlpha(0.6f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(48), dp(14));
        root.addView(footer, flp);

        setContentView(root);
        applyTheme();
        buildRow(true);
        updateClock();
    }

    private void applyTheme() {
        wallpaper.setImageResource(night ? R.drawable.wall_night : R.drawable.wall_day);
        themeIcon.setImageResource(night ? R.drawable.ic_sun : R.drawable.ic_moon);
        headerPill.setBackground(roundRect(Color.argb(night ? 70 : 90, 0, 0, 0), 0, dp(30)));
    }

    private FrameLayout roundButton(final ImageView icon, int iconRes, View.OnClickListener click) {
        final FrameLayout b = new FrameLayout(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        b.addView(icon, ilp);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        b.setBackground(null);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                b.setBackground(hasFocus ? roundRect(Color.WHITE, 0, dp(22)) : null);
                icon.setColorFilter(hasFocus ? Color.parseColor("#1A1F24") : Color.WHITE,
                        PorterDuff.Mode.SRC_IN);
                b.animate().scaleX(hasFocus ? 1.1f : 1f).scaleY(hasFocus ? 1.1f : 1f)
                        .setDuration(120).start();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.rightMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView shadowText(String s, int sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sizeSp);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        else t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setShadowLayer(dp(4), 0, dp(1), Color.argb(140, 0, 0, 0));
        t.setSingleLine(true);
        return t;
    }

    private void buildRow(boolean requestFocus) {
        if (row == null) return;

        String focusedTag = null;
        View cur = getCurrentFocus();
        if (cur != null && cur.getTag() instanceof String) {
            focusedTag = (String) cur.getTag();
        }

        row.removeAllViews();
        List<String> favs = cleanFavorites();
        for (int i = 0; i < favs.size(); i++) {
            row.addView(appTile(favs.get(i)));
        }
        row.addView(addTile());

        View target = null;
        if (focusedTag != null) target = row.findViewWithTag(focusedTag);
        if (target == null && (requestFocus || cur == null)) target = row.getChildAt(0);
        if (target != null) {
            final View t = target;
            t.post(new Runnable() {
                @Override
                public void run() {
                    t.requestFocus();
                }
            });
        }
    }

    private void focusFirstTile() {
        if (row != null && row.getChildCount() > 0) {
            rowScroll.smoothScrollTo(0, 0);
            row.getChildAt(0).requestFocus();
        }
    }

    private View appTile(final String pkg) {
        final String label = labelOf(pkg);
        FrameLayout tile = baseTile(pkg, label);

        Drawable banner = null;
        Drawable icon = null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            Intent li = launchIntent(pkg);
            if (li != null && li.getComponent() != null) {
                try {
                    banner = pm.getActivityBanner(li.getComponent());
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            if (banner == null) banner = pm.getApplicationBanner(ai);
            if (banner == null) icon = pm.getApplicationIcon(ai);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        if (banner != null) {
            // TV uygulaması: kendi banner görseli kutucuğu doldurur.
            ImageView img = new ImageView(this);
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            // Telefon uygulaması: beyaz kutu, ortada simge + ad.
            tile.setBackgroundColor(Color.parseColor("#F4F5F7"));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            ImageView img = new ImageView(this);
            img.setImageDrawable(icon);
            box.addView(img, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView t = new TextView(this);
            t.setText(label);
            t.setTextColor(Color.parseColor("#1A1F24"));
            t.setTextSize(12);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(8), dp(4), dp(8), 0);
            box.addView(t);
            tile.addView(box, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(pkg);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showTileMenu(pkg);
                return true;
            }
        });
        tile.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                    showTileMenu(pkg);
                    return true;
                }
                return false;
            }
        });
        return tile;
    }

    private View addTile() {
        FrameLayout tile = baseTile(TAG_ADD, "Uygulama ekle");
        tile.setBackgroundColor(Color.argb(70, 255, 255, 255));
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_add);
        plus.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        tile.addView(plus, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        // + kutucuğu diğerlerinden dar olsun.
        tile.getLayoutParams().width = dp(TILE_H);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPicker();
            }
        });
        return tile;
    }

    private FrameLayout baseTile(String tag, final String label) {
        final FrameLayout tile = new FrameLayout(this);
        tile.setTag(tag);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundColor(Color.parseColor("#2A2F36"));

        // Yuvarlak köşe kırpma
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(TILE_RADIUS));
            }
        });
        tile.setClipToOutline(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        lp.rightMargin = dp(16);
        tile.setLayoutParams(lp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.12f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(140).start();
                v.setElevation(hasFocus ? dp(10) : 0);
                tile.setForeground(hasFocus ? roundRect(Color.TRANSPARENT, Color.WHITE, dp(TILE_RADIUS)) : null);
                if (hasFocus) focusedName.setText(label);
            }
        });
        return tile;
    }

    // ------------------------------------------------------------------ diyaloglar

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, night
                ? AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
    }

    private void showAppPicker() {
        final Map<String, String> all = allApps();
        final List<String> favs = getFavorites();
        final String[] pkgs = all.keySet().toArray(new String[0]);
        final String[] labels = all.values().toArray(new String[0]);
        final boolean[] checked = new boolean[pkgs.length];
        for (int i = 0; i < pkgs.length; i++) checked[i] = favs.contains(pkgs[i]);

        dialog()
                .setTitle("Ana ekranda görünecek uygulamalar")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        // Mevcut sırayı koru, yenileri sona ekle.
                        List<String> result = new ArrayList<>();
                        for (String f : favs) {
                            int i = indexOf(pkgs, f);
                            if (i >= 0 && checked[i]) result.add(f);
                        }
                        for (int i = 0; i < pkgs.length; i++) {
                            if (checked[i] && !result.contains(pkgs[i])) result.add(pkgs[i]);
                        }
                        saveFavorites(result);
                        buildRow(false);
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showTileMenu(final String pkg) {
        String[] items = {"◀ Sola taşı", "Sağa taşı ▶", "Ana ekrandan kaldır", "Uygulama bilgisi"};
        dialog()
                .setTitle(labelOf(pkg))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        List<String> favs = getFavorites();
                        int i = favs.indexOf(pkg);
                        if (i < 0) return;
                        switch (which) {
                            case 0:
                                if (i > 0) Collections.swap(favs, i, i - 1);
                                break;
                            case 1:
                                if (i < favs.size() - 1) Collections.swap(favs, i, i + 1);
                                break;
                            case 2:
                                favs.remove(i);
                                break;
                            case 3:
                                openAppInfo(pkg);
                                return;
                        }
                        saveFavorites(favs);
                        buildRow(false);
                        View t = row.findViewWithTag(pkg);
                        if (t != null) t.requestFocus();
                        else focusFirstTile();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ eylemler

    private void launch(String pkg) {
        Intent i = launchIntent(pkg);
        if (i == null) {
            toast("Uygulama açılamadı");
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("Uygulama açılamadı");
        }
    }

    private Intent launchIntent(String pkg) {
        Intent i = null;
        try {
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
        } catch (Exception ignored) {
        }
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            toast("Ayarlar açılamadı");
        }
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            toast("Uygulama bilgisi açılamadı");
        }
    }

    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Sade Ev");
        send.putExtra(Intent.EXTRA_TEXT, "Sade Ev v" + versionName()
                + " — Android TV için sade ana ekran.\n"
                + "https://github.com/karadenizmehmet-debug/Android-launcher\n"
                + "Mehmet Karadeniz, 2026-2027");
        try {
            startActivity(Intent.createChooser(send, "Paylaş"));
        } catch (ActivityNotFoundException e) {
            toast("Bu TV'de paylaşım uygulaması yok");
        }
    }

    // ------------------------------------------------------------------ veri

    private Map<String, String> allApps() {
        Map<String, String> map = new LinkedHashMap<>();
        List<ResolveInfo> list = new ArrayList<>();
        list.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        list.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));

        List<String[]> pairs = new ArrayList<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean dup = false;
            for (String[] p : pairs) if (p[0].equals(pkg)) { dup = true; break; }
            if (dup) continue;
            pairs.add(new String[]{pkg, ri.loadLabel(pm).toString()});
        }
        final Collator col = Collator.getInstance(TR);
        Collections.sort(pairs, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return col.compare(a[1], b[1]);
            }
        });
        for (String[] p : pairs) map.put(p[0], p[1]);
        return map;
    }

    private void seedFavorites() {
        List<String> seed = new ArrayList<>();
        List<ResolveInfo> tv = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        for (ResolveInfo ri : tv) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || seed.contains(pkg)) continue;
            seed.add(pkg);
            if (seed.size() >= FIRST_RUN_APP_COUNT) break;
        }
        saveFavorites(seed);
        prefs.edit().putBoolean(KEY_INIT, true).apply();
    }

    private List<String> getFavorites() {
        List<String> out = new ArrayList<>();
        String raw = prefs.getString(KEY_FAVS, "");
        if (raw.isEmpty()) return out;
        for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    /** Kaldırılmış uygulamaları listeden düşürür. */
    private List<String> cleanFavorites() {
        List<String> favs = getFavorites();
        List<String> ok = new ArrayList<>();
        for (String p : favs) if (launchIntent(p) != null) ok.add(p);
        if (ok.size() != favs.size()) saveFavorites(ok);
        return ok;
    }

    private void saveFavorites(List<String> favs) {
        prefs.edit().putString(KEY_FAVS, TextUtils.join(",", favs)).apply();
    }

    // ------------------------------------------------------------------ yardımcılar

    private void updateClock() {
        if (clockView == null) return;
        clockView.setText(new SimpleDateFormat("HH:mm", TR).format(new Date()));
    }

    private String labelOf(String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private String versionName() {
        try {
            return pm.getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.1";
        }
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return -1;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(dp(3), stroke);
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
