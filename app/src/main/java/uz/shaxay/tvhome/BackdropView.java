package uz.shaxay.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;
import android.view.View;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Render color grades off the UI thread; the TV GPU only blends ordinary cached bitmaps. */
final class BackdropView extends View {
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final RectF destination = new RectF();
  private final Bitmap image;
  private final LruCache<Integer, Bitmap> scenes =
      new LruCache<Integer, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(Integer color, Bitmap bitmap) {
          return bitmap.getAllocationByteCount();
        }
      };
  private final ExecutorService renderer = Executors.newSingleThreadExecutor();
  private Future<?> pending;
  private ValueAnimator transition;
  private Bitmap current, next;
  private float blend;
  private int target = Color.rgb(120, 171, 255);
  private int generation;
  private boolean selected, disposed;

  BackdropView(Context context) {
    super(context);
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    options.inPreferredConfig = Bitmap.Config.RGB_565;
    image = BitmapFactory.decodeResource(getResources(), R.drawable.console_wallpaper, options);
  }

  void select(int color) {
    if (selected && color == target) return;
    selected = true;
    target = color;
    int request = ++generation;
    if (pending != null) pending.cancel(false);
    Bitmap cached = scenes.get(color);
    if (cached != null) {
      show(cached);
      return;
    }
    pending =
        renderer.submit(
            () -> {
              android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
              Bitmap graded = renderScene(color);
              post(
                  () -> {
                    if (disposed) return;
                    scenes.put(color, graded);
                    if (request == generation) show(graded);
                  });
            });
  }

  private Bitmap renderScene(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.RGB_565);
    Canvas c = new Canvas(bitmap);
    Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    int r = Math.min(255, 41 + Color.red(color) * 9 / 10);
    int g = Math.min(255, 41 + Color.green(color) * 9 / 10);
    int b = Math.min(255, 41 + Color.blue(color) * 9 / 10);
    p.setColorFilter(new LightingColorFilter(Color.rgb(r, g, b), Color.rgb(2, 3, 6)));
    c.drawBitmap(image, null, new RectF(0, 0, 1280, 720), p);
    p.setColorFilter(null);
    p.setShader(
        new LinearGradient(
            0, 0, 1000, 0, Color.argb(190, 2, 5, 12), Color.TRANSPARENT, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 1280, 720, p);
    p.setShader(
        new LinearGradient(
            0, 0, 0, 300, Color.argb(125, 2, 5, 12), Color.TRANSPARENT, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 1280, 720, p);
    return bitmap;
  }

  private void show(Bitmap bitmap) {
    if (transition != null) transition.cancel();
    if (next != null && blend >= .5f) current = next;
    next = null;
    if (current == null) {
      current = bitmap;
      invalidate();
      return;
    }
    if (current == bitmap) {
      invalidate();
      return;
    }
    next = bitmap;
    blend = 0;
    transition = ValueAnimator.ofFloat(0, 1);
    transition.setDuration(180);
    transition.addUpdateListener(
        value -> {
          blend = (Float) value.getAnimatedValue();
          if (blend == 1) {
            current = next;
            next = null;
          }
          invalidate();
        });
    transition.start();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    destination.set(0, 0, w, h);
  }

  @Override
  protected void onDraw(Canvas c) {
    if (current == null) {
      c.drawColor(Color.rgb(4, 7, 12));
      return;
    }
    paint.setAlpha(255);
    c.drawBitmap(current, null, destination, paint);
    if (next != null) {
      paint.setAlpha(Math.round(blend * 255));
      c.drawBitmap(next, null, destination, paint);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    disposed = true;
    if (transition != null) transition.cancel();
    if (pending != null) pending.cancel(true);
    renderer.shutdownNow();
    super.onDetachedFromWindow();
  }
}
