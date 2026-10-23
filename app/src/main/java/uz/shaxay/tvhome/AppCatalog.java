package uz.shaxay.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Launchable applications and their bounded, in-memory presentation caches. */
final class AppCatalog {
  private final PackageManager packageManager;
  private final String ownPackage;
  private final Map<String, ResolveInfo> apps = new LinkedHashMap<>();
  private final Map<String, String> labels = new LinkedHashMap<>();
  private final List<String> sortedApps = new ArrayList<>();
  private final LruCache<String, Bitmap> icons =
      new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override
        protected int sizeOf(String name, Bitmap icon) {
          return icon.getAllocationByteCount();
        }
      };
  private boolean sorted;

  AppCatalog(Context context) {
    packageManager = context.getPackageManager();
    ownPackage = context.getPackageName();
  }

  void reload() {
    apps.clear();
    labels.clear();
    for (String category :
        new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
      Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
      for (ResolveInfo info : packageManager.queryIntentActivities(query, 0)) {
        String name = info.activityInfo.packageName;
        if (isVisible(name) && !apps.containsKey(name)) apps.put(name, info);
      }
    }
    sortedApps.clear();
    sortedApps.addAll(apps.keySet());
    sorted = false;
  }

  private boolean isVisible(String name) {
    return !name.equals(ownPackage)
        && !name.equals("com.google.android.tvlauncher")
        && !name.equals("com.mitv.tvhome.atv")
        && !name.equals("com.mitv.tvhome.oemtab");
  }

  boolean contains(String name) {
    return apps.containsKey(name);
  }

  List<String> sortedPackages() {
    if (!sorted) {
      Collator collator = Collator.getInstance(Locale.getDefault());
      Collections.sort(sortedApps, (left, right) -> collator.compare(label(left), label(right)));
      sorted = true;
    }
    return Collections.unmodifiableList(sortedApps);
  }

  String label(String name) {
    String cached = labels.get(name);
    if (cached == null) {
      cached = apps.get(name).loadLabel(packageManager).toString();
      labels.put(name, cached);
    }
    return cached;
  }

  void invalidateIcon(String name) {
    icons.remove(name);
  }

  Bitmap icon(String name) {
    Bitmap cached = icons.get(name);
    if (cached != null) return cached;
    Drawable drawable = apps.get(name).loadIcon(packageManager);
    Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    drawable.setBounds(0, 0, 128, 128);
    drawable.draw(new Canvas(bitmap));
    Bitmap rounded = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(rounded);
    Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    mask.setColor(Color.WHITE);
    canvas.drawRoundRect(new RectF(0, 0, 128, 128), 22, 22, mask);
    mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(bitmap, 0, 0, mask);
    bitmap.recycle();
    icons.put(name, rounded);
    return rounded;
  }

  Intent launchIntent(String name) {
    ResolveInfo info = apps.get(name);
    if (info == null) return null;
    return new Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
  }
}
