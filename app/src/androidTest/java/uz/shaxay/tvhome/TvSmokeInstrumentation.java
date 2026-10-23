package uz.shaxay.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Native integration check using real D-pad events; no UIAutomator or test dependencies. */
public class TvSmokeInstrumentation extends Instrumentation {
  private Activity activity;
  private int passed;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  private void ui(Runnable action) {
    runOnMainSync(action);
    waitForIdleSync();
    SystemClock.sleep(120);
  }

  private Object field(String name) {
    try {
      Field f = MainActivity.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(activity);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private String focus() {
    AtomicReference<String> value = new AtomicReference<>("");
    ui(
        () -> {
          View view = activity.getCurrentFocus();
          if (view != null)
            value.set(
                view.getContentDescription() != null
                    ? view.getContentDescription().toString()
                    : view instanceof TextView ? ((TextView) view).getText().toString() : "");
        });
    return value.get();
  }

  private List<String> names() {
    List<String> result = new ArrayList<>();
    ui(
        () -> {
          ViewGroup strip = (ViewGroup) field("grid");
          for (int i = 0; i < strip.getChildCount(); i++) {
            CharSequence label = strip.getChildAt(i).getContentDescription();
            if (label != null) result.add(label.toString());
          }
        });
    return result;
  }

  private void check(String name, boolean condition) {
    if (!condition) throw new AssertionError(name);
    passed++;
    Bundle event = new Bundle();
    event.putString("stream", "PASS: " + name + "\n");
    sendStatus(0, event);
  }

  private void key(int key) {
    sendKeyDownUpSync(key);
    waitForIdleSync();
    SystemClock.sleep(130);
  }

  private void open() {
    // Match a TV remote even when the test runs on a touchscreen emulator.
    setInTouchMode(false);
    activity =
        startActivitySync(
            new Intent(Intent.ACTION_MAIN)
                .setClassName(getTargetContext(), "uz.shaxay.tvhome.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    waitForIdleSync();
    SystemClock.sleep(300);
    key(KeyEvent.KEYCODE_BACK);
  }

  private AlertDialog menu() {
    long down = SystemClock.uptimeMillis();
    sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
    SystemClock.sleep(800);
    sendKeySync(
        new KeyEvent(
            down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
    waitForIdleSync();
    AlertDialog dialog = (AlertDialog) field("appDialog");
    check("Long OK opens the app menu", dialog != null && dialog.isShowing());
    return dialog;
  }

  private void choose(AlertDialog dialog, int label) {
    String name = getTargetContext().getString(label);
    ui(
        () -> {
          for (int i = 0; i < dialog.getListView().getAdapter().getCount(); i++) {
            if (name.equals(dialog.getListView().getAdapter().getItem(i).toString())) {
              dialog.getListView().requestFocus();
              dialog.getListView().setSelection(i);
              return;
            }
          }
          throw new AssertionError("Missing action: " + name);
        });
    key(KeyEvent.KEYCODE_DPAD_CENTER);
  }

  @Override
  public void onStart() {
    super.onStart();
    Bundle result = new Bundle();
    SharedPreferences preferences = getTargetContext().getSharedPreferences("home", 0);
    String backup = preferences.getString("favorites", null);
    int status = Activity.RESULT_CANCELED;
    try {
      AppCatalog catalog = new AppCatalog(getTargetContext());
      catalog.reload();
      List<String> available = catalog.sortedPackages();
      check(
          "At least two launchable apps are available for the navigation fixture",
          available.size() >= 2);
      FavoritesStore store = new FavoritesStore(getTargetContext());
      store.save(Collections.emptyList());
      check("An intentionally empty favorites list is preserved", store.load(catalog).isEmpty());
      preferences
          .edit()
          .putString("favorites", "[\"missing.package\",\"missing.package\"]")
          .commit();
      check(
          "Stored duplicates are removed without dropping an unavailable favorite",
          store.load(catalog).equals(Collections.singletonList("missing.package")));
      preferences.edit().putString("favorites", "[invalid-json").commit();
      check(
          "Malformed preferences fall back to installed applications",
          available.containsAll(store.load(catalog)));
      List<String> fixture = new ArrayList<>(available.subList(0, Math.min(8, available.size())));
      store.save(fixture);
      check("Favorites retain their stored order", store.load(catalog).equals(fixture));
      open();
      List<String> original = names();
      check("Fixture favorites loaded", original.size() == fixture.size());
      String focused = focus();
      check(
          "First app focused (expected " + original.get(0) + ", got " + focused + ")",
          focused.equals(original.get(0)));
      View firstTile = ((ViewGroup) field("grid")).getChildAt(0);
      ui(
          () ->
              callActivityOnNewIntent(
                  activity, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));
      check(
          "Home reuses the existing app views",
          ((ViewGroup) field("grid")).getChildAt(0) == firstTile);
      Object background = field("backdrop");
      Field targetColor = background.getClass().getDeclaredField("target");
      targetColor.setAccessible(true);
      // Generic apps may share an accent. Seed a distinct color to verify the focus callback.
      ui(() -> ((BackdropView) background).select(Color.MAGENTA));
      key(KeyEvent.KEYCODE_DPAD_RIGHT);
      String nextFocus = focus();
      check(
          "Right selects next app (expected " + original.get(1) + ", got " + nextFocus + ")",
          nextFocus.equals(original.get(1)));
      check(
          "Wallpaper color follows the selected app",
          targetColor.getInt(background) != Color.MAGENTA);
      AtomicReference<String> title = new AtomicReference<>();
      ui(() -> title.set(((TextView) field("section")).getText().toString()));
      check("Hero updates with selection", original.get(1).equals(title.get()));
      key(KeyEvent.KEYCODE_DPAD_DOWN);
      check(
          "Down reaches launch button",
          focus().equals(getTargetContext().getString(R.string.open_app, original.get(1))));
      key(KeyEvent.KEYCODE_DPAD_UP);
      check("Up restores selection", focus().equals(original.get(1)));
      key(KeyEvent.KEYCODE_DPAD_UP);
      check("Up reaches navigation", focus().equals(getTargetContext().getString(R.string.home)));
      key(KeyEvent.KEYCODE_DPAD_RIGHT);
      key(KeyEvent.KEYCODE_DPAD_CENTER);
      List<String> all = names();
      check("All installed launchable apps available", all.size() >= original.size());
      for (int i = 0; i < all.size() + 1; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
      check("Carousel reaches final app", focus().equals(all.get(all.size() - 1)));
      key(KeyEvent.KEYCODE_BACK);
      check("Back returns to favorites", names().equals(original));
      choose(menu(), R.string.move_later);
      List<String> moved = new ArrayList<>(original);
      Collections.swap(moved, 0, 1);
      check("Reordering updates the carousel", names().equals(moved));
      ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
      ui(() -> activity.recreate());
      Activity recreated = waitForMonitorWithTimeout(monitor, 5000);
      removeMonitor(monitor);
      if (recreated == null) throw new AssertionError("Activity did not recreate");
      activity = recreated;
      waitForIdleSync();
      key(KeyEvent.KEYCODE_BACK);
      check("Reordering survives activity restart", names().equals(moved));
      key(KeyEvent.KEYCODE_DPAD_RIGHT);
      choose(menu(), R.string.move_earlier);
      check("Original order restored", names().equals(original));
      key(KeyEvent.KEYCODE_BACK);
      AlertDialog dialog = menu();
      choose(dialog, R.string.remove_favorite);
      check("Removing a favorite updates the carousel", names().size() == original.size() - 1);
      result.putInt("passed", passed);
      result.putString("stream", "OK: " + passed + " native TV integration checks\n");
      status = Activity.RESULT_OK;
    } catch (Throwable error) {
      result.putString("stream", android.util.Log.getStackTraceString(error));
    } finally {
      if (backup != null) preferences.edit().putString("favorites", backup).commit();
      else preferences.edit().remove("favorites").commit();
      if (activity != null) ui(() -> activity.finish());
    }
    finish(status, result);
  }
}
