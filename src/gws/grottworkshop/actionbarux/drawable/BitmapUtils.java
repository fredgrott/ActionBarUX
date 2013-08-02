package gws.grottworkshop.actionbarux.drawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

public class BitmapUtils {

	/**
	 * Creates a 'ghost' bitmap version of the given source drawable (ideally a BitmapDrawable).
	 * In the ghost bitmap, the RGB values take on the values from the 'color' argument, while
	 * the alpha values are derived from the source's grayscaled RGB values. The effect is that
	 * you can see through darker parts of the source bitmap, while lighter parts show up as
	 * the given color. The 'invert' argument inverts the computation of alpha values, and looks
	 * best when the given color is a dark.
	 */

	@SuppressWarnings("unused")
	private Bitmap createGhostIcon(Drawable src, int color, boolean invert) {
	    int width = src.getIntrinsicWidth();
	    int height = src.getIntrinsicHeight();
	    if (width <= 0 || height <= 0) {
	        throw new UnsupportedOperationException("Source drawable needs an intrinsic size.");
	    }

	    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	    Canvas canvas = new Canvas(bitmap);
	    Paint colorToAlphaPaint = new Paint();
	    int invMul = invert ? -1 : 1;
	    colorToAlphaPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
	            0, 0, 0, 0, Color.red(color),
	            0, 0, 0, 0, Color.green(color),
	            0, 0, 0, 0, Color.blue(color),
	            invMul * 0.213f, invMul * 0.715f, invMul * 0.072f, 0, invert ? 255 : 0,
	    })));
	    canvas.saveLayer(0, 0, width, height, colorToAlphaPaint, Canvas.ALL_SAVE_FLAG);
	    canvas.drawColor(invert ? Color.WHITE : Color.BLACK);
	    src.setBounds(0, 0, width, height);
	    src.draw(canvas);
	    canvas.restore();
	    return bitmap;
	}
}
