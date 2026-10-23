package uz.shaxay.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline TV home. No permissions, services, analytics, or third-party dependencies. */
public class MainActivity extends Activity {
  private static final int TEXT = Color.rgb(240, 244, 242);
  private AppCatalog catalog;
  private FavoritesStore favoritesStore;
  private List<String> favorites;
  private final List<View> tiles = new ArrayList<>();
  private TextView favoritesTab, allTab, section, launchButton, menuButton;
  private final List<TextView> navButtons = new ArrayList<>();
  private BackdropView backdrop;
  private HorizontalScrollView scroll;
  private LinearLayout grid;
  private boolean allApps;
  private String lastPackage;
  private boolean receiverRegistered;
  private AlertDialog appDialog;
  private AlertDialog inputDialog;
  private boolean appsDirty;

  private final BroadcastReceiver packageReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          appsDirty = true;
          String changed =
              intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
          if (changed != null) catalog.invalidateIcon(changed);
          if (hasWindowFocus()) {
            loadApps();
            renderGrid();
          }
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    catalog = new AppCatalog(this);
    favoritesStore = new FavoritesStore(this);
    if (state != null) {
      allApps = state.getBoolean("allApps");
      lastPackage = state.getString("lastPackage");
    }
    loadApps();
    favorites = favoritesStore.load(catalog);
    buildScreen();
    renderGrid();
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addDataScheme("package");
    registerReceiver(packageReceiver, filter);
    receiverRegistered = true;
  }

  @Override
  protected void onResume() {
    super.onResume();
    immersive();
    if (appsDirty) {
      loadApps();
      renderGrid();
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent.hasCategory(Intent.CATEGORY_HOME)) {
      if (appDialog != null) appDialog.dismiss();
      if (inputDialog != null) inputDialog.dismiss();
      if (allApps || appsDirty) {
        allApps = false;
        lastPackage = null;
        if (appsDirty) loadApps();
        renderGrid();
      } else if (!tiles.isEmpty()) tiles.get(0).requestFocus();
      scroll.scrollTo(0, 0);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putBoolean("allApps", allApps);
    out.putString("lastPackage", lastPackage);
  }

  @Override
  protected void onDestroy() {
    if (receiverRegistered) unregisterReceiver(packageReceiver);
    if (appDialog != null) appDialog.dismiss();
    if (inputDialog != null) inputDialog.dismiss();
    super.onDestroy();
  }

  private void immersive() {
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, float size, int color, boolean bold) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(size);
    view.setTextColor(color);
    view.setGravity(Gravity.CENTER_VERTICAL);
    view.setFontFeatureSettings("kern");
    if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return view;
  }

  private GradientDrawable surface(int color, int stroke) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(dp(12));
    if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(2), stroke);
    return drawable;
  }

  private LinearLayout horizontal() {
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.HORIZONTAL);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  private void buildScreen() {
    FrameLayout stage = new FrameLayout(this);
    backdrop = new BackdropView(this);
    stage.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(48), dp(27), dp(48), dp(25));
    root.setClipChildren(false);
    stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
    setContentView(stage);

    LinearLayout header = horizontal();
    root.addView(header, new LinearLayout.LayoutParams(-1, dp(42)));
    favoritesTab = button(getString(R.string.home), () -> selectTab(false));
    allTab = button(getString(R.string.applications), () -> selectTab(true));
    header.addView(favoritesTab, navButtonParams());
    header.addView(allTab, navButtonParams());
    header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    TextView inputs =
        button(
            getString(R.string.inputs),
            () -> {
              inputDialog = TvInputs.show(this, this::launch);
            });
    TextView settings =
        button(getString(R.string.settings), () -> launch(new Intent(Settings.ACTION_SETTINGS)));
    header.addView(inputs, navButtonParams());
    header.addView(settings, navButtonParams());
    Collections.addAll(navButtons, favoritesTab, allTab, inputs, settings);
    TextClock clock = new TextClock(this);
    clock.setFormat12Hour("HH:mm");
    clock.setFormat24Hour("HH:mm");
    clock.setTextSize(21);
    clock.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
    clock.setTextColor(TEXT);
    header.addView(clock, new LinearLayout.LayoutParams(dp(78), -1));

    scroll = new HorizontalScrollView(this);
    scroll.setClipToPadding(false);
    scroll.setClipChildren(false);
    scroll.setPadding(dp(9), dp(12), dp(9), dp(12));
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    scroll.setFocusable(false);
    LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(-1, dp(120));
    stripParams.topMargin = dp(22);
    root.addView(scroll, stripParams);
    grid = horizontal();
    grid.setClipChildren(false);
    grid.setClipToPadding(false);
    scroll.addView(grid, new HorizontalScrollView.LayoutParams(-2, -1));

    FrameLayout hero = new FrameLayout(this);
    LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, 0, 1);
    heroParams.topMargin = dp(15);
    root.addView(hero, heroParams);
    LinearLayout details = new LinearLayout(this);
    details.setOrientation(LinearLayout.VERTICAL);
    details.setGravity(Gravity.CENTER_VERTICAL);
    hero.addView(details, new FrameLayout.LayoutParams(dp(460), -1, Gravity.START));
    section = text("", 46, TEXT, true);
    section.setMaxLines(2);
    section.setEllipsize(TextUtils.TruncateAt.END);
    details.addView(section, new LinearLayout.LayoutParams(dp(450), -2));
    LinearLayout bottom = horizontal();
    LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, dp(48));
    bottomParams.topMargin = dp(20);
    root.addView(bottom, bottomParams);
    launchButton = button(getString(R.string.open), () -> launchSelected());
    launchButton.setGravity(Gravity.CENTER);
    launchButton.setTextSize(17);
    styleButton(launchButton, 1);
    bottom.addView(launchButton, new LinearLayout.LayoutParams(dp(190), -1));
    menuButton =
        button(
            "•••",
            () -> {
              if (lastPackage != null && catalog.contains(lastPackage)) appMenu(lastPackage);
            });
    menuButton.setContentDescription(getString(R.string.app_actions));
    menuButton.setGravity(Gravity.CENTER);
    styleButton(menuButton, 2);
    LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(62), -1);
    menuParams.leftMargin = dp(14);
    bottom.addView(menuButton, menuParams);
    bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    launchButton.setNextFocusLeftId(launchButton.getId());
    launchButton.setNextFocusRightId(menuButton.getId());
    menuButton.setNextFocusLeftId(launchButton.getId());
    menuButton.setNextFocusRightId(menuButton.getId());
  }

  private LinearLayout.LayoutParams navButtonParams() {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -1);
    p.rightMargin = dp(8);
    return p;
  }

  private TextView button(String label, Runnable click) {
    TextView view = text(label, 14, TEXT, true);
    view.setPadding(dp(13), 0, dp(13), 0);
    view.setId(View.generateViewId());
    view.setFocusable(true);
    view.setClickable(true);
    view.setOnClickListener(v -> click.run());
    styleButton(view, 0);
    return view;
  }

  private void styleButton(TextView view, int kind) {
    StateListDrawable states = new StateListDrawable();
    states.addState(
        new int[] {android.R.attr.state_focused},
        surface(Color.WHITE, kind == 1 ? Color.rgb(171, 204, 255) : Color.WHITE));
    states.addState(
        new int[] {android.R.attr.state_selected},
        surface(Color.argb(20, 220, 234, 255), Color.TRANSPARENT));
    states.addState(
        new int[] {},
        surface(
            kind == 1
                ? Color.rgb(229, 237, 250)
                : kind == 2 ? Color.argb(27, 220, 234, 255) : Color.TRANSPARENT,
            Color.TRANSPARENT));
    view.setBackground(states);
    view.setTextColor(
        new ColorStateList(
            new int[][] {{android.R.attr.state_focused}, {android.R.attr.state_selected}, {}},
            new int[] {
              Color.rgb(12, 25, 46),
              Color.WHITE,
              kind == 1 ? Color.rgb(12, 25, 46) : Color.rgb(167, 184, 209)
            }));
  }

  private void selectTab(boolean all) {
    allApps = all;
    lastPackage = null;
    renderGrid();
    scroll.scrollTo(0, 0);
  }

  private void loadApps() {
    catalog.reload();
    appsDirty = false;
  }

  private void renderGrid() {
    if (grid == null) return;
    // Capture the selection before removing focused children: Android can move focus during
    // rebuilding.
    String wanted = lastPackage;
    grid.removeAllViews();
    tiles.clear();
    favoritesTab.setSelected(!allApps);
    allTab.setSelected(allApps);
    List<String> visible = new ArrayList<>();
    if (allApps) {
      visible.addAll(catalog.sortedPackages());
    } else for (String name : favorites) if (catalog.contains(name)) visible.add(name);
    if (visible.isEmpty()) {
      lastPackage = null;
      section.setText(R.string.empty_favorites);
      backdrop.select(Color.rgb(120, 171, 255));
      launchButton.setEnabled(false);
      menuButton.setEnabled(false);
      allTab.requestFocus();
      return;
    }
    launchButton.setEnabled(true);
    menuButton.setEnabled(true);
    View restore = null;
    for (int i = 0; i < visible.size(); i++) {
      String name = visible.get(i);
      View tile = appTile(name);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(86), dp(86));
      p.rightMargin = dp(18);
      grid.addView(tile, p);
      tiles.add(tile);
      if (name.equals(wanted)) restore = tile;
    }
    for (int i = 0; i < tiles.size(); i++) {
      View tile = tiles.get(i);
      tile.setNextFocusLeftId(tiles.get(Math.max(0, i - 1)).getId());
      tile.setNextFocusRightId(tiles.get(Math.min(tiles.size() - 1, i + 1)).getId());
      tile.setNextFocusUpId((allApps ? allTab : favoritesTab).getId());
      tile.setNextFocusDownId(launchButton.getId());
    }
    View target = restore != null ? restore : tiles.get(0);
    target.requestFocus();
    int selected = tiles.indexOf(target);
    updateHero(visible.get(selected), target);
    target.post(() -> target.requestFocus());
  }

  private View appTile(String name) {
    FrameLayout tile = new FrameLayout(this);
    tile.setId(View.generateViewId());
    tile.setPadding(0, 0, 0, 0);
    tile.setFocusable(true);
    tile.setClickable(true);
    tile.setLongClickable(true);
    GradientDrawable glow = new GradientDrawable();
    glow.setShape(GradientDrawable.OVAL);
    glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
    glow.setGradientRadius(dp(44));
    glow.setColors(new int[] {Color.argb(90, 179, 211, 255), Color.TRANSPARENT});
    StateListDrawable tileStates = new StateListDrawable();
    tileStates.addState(new int[] {android.R.attr.state_focused}, glow);
    tileStates.addState(
        new int[] {}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    tile.setBackground(tileStates);
    tile.setContentDescription(catalog.label(name));
    ImageView icon = new ImageView(this);
    icon.setImageBitmap(catalog.icon(name));
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    icon.setPadding(dp(9), dp(9), dp(9), dp(9));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
    tile.setOnClickListener(v -> launchApp(name));
    tile.setOnLongClickListener(
        v -> {
          appMenu(name);
          return true;
        });
    tile.setOnKeyListener(
        (v, keyCode, event) -> {
          if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
              && event.getAction() == KeyEvent.ACTION_DOWN
              && event.isLongPress()) {
            v.cancelLongPress();
            appMenu(name);
            return true;
          }
          return false;
        });
    tile.setOnFocusChangeListener(
        (v, focus) -> {
          v.animate()
              .scaleX(focus ? 1.20f : 1f)
              .scaleY(focus ? 1.20f : 1f)
              .setDuration(130)
              .withLayer()
              .start();
          if (focus) updateHero(name, v);
        });
    return tile;
  }

  private void updateHero(String name, View selected) {
    lastPackage = name;
    section.setText(catalog.label(name));
    int accent =
        name.contains("youtube")
            ? Color.rgb(255, 86, 109)
            : name.contains("spotify")
                ? Color.rgb(94, 214, 149)
                : name.contains("kinopoisk")
                    ? Color.rgb(255, 124, 65)
                    : name.contains("allplay")
                        ? Color.rgb(255, 181, 82)
                        : name.contains("appletv")
                            ? Color.rgb(210, 224, 245)
                            : name.contains("ivi")
                                ? Color.rgb(255, 85, 147)
                                : name.contains("okko")
                                    ? Color.rgb(174, 139, 245)
                                    : Color.rgb(124, 181, 255);
    backdrop.select(accent);
    for (TextView nav : navButtons) nav.setNextFocusDownId(selected.getId());
    launchButton.setNextFocusUpId(selected.getId());
    menuButton.setNextFocusUpId(selected.getId());
    launchButton.setContentDescription(getString(R.string.open_app, catalog.label(name)));
  }

  private void launchSelected() {
    if (lastPackage != null && catalog.contains(lastPackage)) launchApp(lastPackage);
  }

  private void launchApp(String name) {
    Intent intent = catalog.launchIntent(name);
    if (intent != null) launch(intent);
  }

  private enum AppAction {
    ADD_FAVORITE(R.string.add_favorite),
    REMOVE_FAVORITE(R.string.remove_favorite),
    MOVE_EARLIER(R.string.move_earlier),
    MOVE_LATER(R.string.move_later),
    DETAILS(R.string.app_details);

    final int label;

    AppAction(int label) {
      this.label = label;
    }
  }

  private void appMenu(String name) {
    if (appDialog != null && appDialog.isShowing()) return;
    int index = favorites.indexOf(name);
    List<AppAction> actions = new ArrayList<>();
    actions.add(index >= 0 ? AppAction.REMOVE_FAVORITE : AppAction.ADD_FAVORITE);
    if (index > 0) actions.add(AppAction.MOVE_EARLIER);
    if (index >= 0 && index < favorites.size() - 1) actions.add(AppAction.MOVE_LATER);
    actions.add(AppAction.DETAILS);
    String[] labels = new String[actions.size()];
    for (int i = 0; i < actions.size(); i++) labels[i] = getString(actions.get(i).label);
    appDialog =
        new AlertDialog.Builder(this)
            .setTitle(catalog.label(name))
            .setItems(
                labels,
                (dialog, which) -> {
                  switch (actions.get(which)) {
                    case ADD_FAVORITE:
                      favorites.add(name);
                      break;
                    case REMOVE_FAVORITE:
                      favorites.remove(name);
                      break;
                    case MOVE_EARLIER:
                      Collections.swap(favorites, index, index - 1);
                      break;
                    case MOVE_LATER:
                      Collections.swap(favorites, index, index + 1);
                      break;
                    case DETAILS:
                      launch(
                          new Intent(
                              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                              Uri.parse("package:" + name)));
                      return;
                  }
                  favoritesStore.save(favorites);
                  renderGrid();
                })
            .setNegativeButton(R.string.close, null)
            .create();
    appDialog.show();
  }

  private void launch(Intent intent) {
    try {
      startActivity(intent);
    } catch (RuntimeException error) {
      Toast.makeText(this, R.string.launch_error, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onBackPressed() {
    if (allApps) selectTab(false);
    else {
      scroll.smoothScrollTo(0, 0);
      if (!tiles.isEmpty()) tiles.get(0).requestFocus();
      else allTab.requestFocus();
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU
        && lastPackage != null
        && catalog.contains(lastPackage)
        && grid.findFocus() != null) {
      appMenu(lastPackage);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }
}
