// #48 (WS3) | Android decoder for the engine's image type (BitmapFactory). Java 8 source level for Android.
package io.sajeev.engine.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import io.sajeev.image.ImageCodec;
import io.sajeev.image.PixelImage;

/** Install once at start-up with {@link #install()}. */
public final class AndroidImageDecoder implements ImageCodec.Decoder {

    /** Makes the engine decode with BitmapFactory. */
    public static void install() {
        ImageCodec.install(new AndroidImageDecoder());
    }

    @Override
    public PixelImage decode(byte[] encoded) {
        Bitmap b = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
        if (b == null) {
            return null;
        }
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        return new PixelImage(w, h, px);
    }
}
