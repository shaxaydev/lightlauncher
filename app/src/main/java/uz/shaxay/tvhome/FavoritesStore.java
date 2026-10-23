package uz.shaxay.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/** Preserves the preference file and JSON format used by earlier launcher versions. */
final class FavoritesStore {
  private final SharedPreferences preferences;

  FavoritesStore(Context context) {
    preferences = context.getSharedPreferences("home", Context.MODE_PRIVATE);
  }

  List<String> load(AppCatalog catalog) {
    List<String> favorites = new ArrayList<>();
    if (preferences.contains("favorites")) {
      try {
        JSONArray saved = new JSONArray(preferences.getString("favorites", "[]"));
        for (int i = 0; i < saved.length(); i++) {
          String name = saved.getString(i);
          if (!favorites.contains(name)) favorites.add(name);
        }
        return favorites;
      } catch (JSONException ignored) {
        // A malformed local preference falls back to installed defaults.
        favorites.clear();
      }
    }
    for (String name :
        new String[] {
          "com.google.android.youtube.tv",
          "uz.allplay.apptv",
          "ru.kinopoisk.tv",
          "com.spotify.tv.android",
          "com.apple.atve.androidtv.appletv",
          "ru.ivi.client",
          "tv.okko.androidtv",
          "uz.telecom.telecomtv"
        }) {
      if (catalog.contains(name)) favorites.add(name);
    }
    save(favorites);
    return favorites;
  }

  void save(List<String> favorites) {
    preferences.edit().putString("favorites", new JSONArray(favorites).toString()).apply();
  }
}
