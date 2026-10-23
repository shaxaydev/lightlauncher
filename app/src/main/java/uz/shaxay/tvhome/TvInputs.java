package uz.shaxay.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Android TV input selection, including the tested MediaTek fallback. */
final class TvInputs {
  static AlertDialog show(Activity activity, Consumer<Intent> launch) {
    try {
      TvInputManager manager =
          (TvInputManager) activity.getSystemService(Activity.TV_INPUT_SERVICE);
      List<TvInputInfo> inputs = new ArrayList<>();
      List<String> names = new ArrayList<>();
      if (manager != null) {
        for (TvInputInfo input : manager.getTvInputList()) {
          if (!input.isPassthroughInput() || input.isHidden(activity)) continue;
          inputs.add(input);
          CharSequence custom = input.loadCustomLabel(activity);
          String name = (custom == null ? input.loadLabel(activity) : custom).toString();
          if (manager.getInputState(input.getId()) == TvInputManager.INPUT_STATE_DISCONNECTED) {
            name = activity.getString(R.string.input_disconnected, name);
          }
          names.add(name);
        }
      }
      if (inputs.isEmpty()) {
        Intent tv =
            activity
                .getPackageManager()
                .getLeanbackLaunchIntentForPackage("com.mediatek.wwtv.tvcenter");
        if (tv != null) launch.accept(tv);
        else showRemoteHint(activity);
        return null;
      }
      AlertDialog dialog =
          new AlertDialog.Builder(activity)
              .setTitle(R.string.tv_inputs)
              .setItems(
                  names.toArray(new String[0]),
                  (selected, which) ->
                      launch.accept(
                          new Intent(
                              Intent.ACTION_VIEW,
                              TvContract.buildChannelUriForPassthroughInput(
                                  inputs.get(which).getId()))))
              .setNegativeButton(R.string.close, null)
              .create();
      dialog.show();
      return dialog;
    } catch (RuntimeException error) {
      showRemoteHint(activity);
      return null;
    }
  }

  private static void showRemoteHint(Activity activity) {
    Toast.makeText(activity, R.string.use_input_remote, Toast.LENGTH_LONG).show();
  }
}
