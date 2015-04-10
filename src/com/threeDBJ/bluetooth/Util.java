package com.threeDBJ.bluetooth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class Util {
    public static String TAG="BTAndroid";
    public static Util instance = new Util();
    public long readTime;

    public static void AlertBox(final Activity activity, String title, String message) {
	AlertBox(activity, title, message, false);
    }

    public static void AlertBox(final Activity activity, String title, String message, final boolean quit) {
	new AlertDialog.Builder(activity)
	    .setTitle( title )
	    .setMessage( message + " Press OK to exit." )
	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface arg0, int arg1) {
			if(quit) activity.finish();
		    }
		}).show();
    }

    public static void shortToast(Context context, String msg) {
	Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static byte[] readBytes(InputStream input) {
	byte[] ret = null;
	try {
	    ret = new byte[input.available()];
	    input.read(ret);
	} catch(IOException e) {
	    DebugLog.e(TAG, "Unable to read image");
	}
	return ret;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
	// Raw height and width of image
	final int height = options.outHeight;
	final int width = options.outWidth;
	int inSampleSize = 1;

	if (height > reqHeight || width > reqWidth) {

	    // Calculate ratios of height and width to requested height and width
	    final int heightRatio = Math.round((float) height / (float) reqHeight);
	    final int widthRatio = Math.round((float) width / (float) reqWidth);

	    // Choose the smallest ratio as inSampleSize value, this will guarantee
	    // a final image with both dimensions larger than or equal to the
	    // requested height and width.
	    inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
	}

	return inSampleSize;
    }

    public static Bitmap downsizeImage(Context context, Uri image) throws Exception {
	InputStream input1 = context.getContentResolver().openInputStream(image);
        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(input1, null, o);

        // The new size we want to scale to
        final int SIZE = 1000;

	DebugLog.e(TAG, "Downsizing from: "+o.outWidth+", "+o.outHeight);
	int sampleSize = calculateInSampleSize(o, SIZE, SIZE);
        o.inSampleSize = sampleSize;
	o.inJustDecodeBounds = false;

	DebugLog.e(TAG, "Downsize scale: "+o.inSampleSize);
	InputStream input2 = context.getContentResolver().openInputStream(image);
        return BitmapFactory.decodeStream(input2, null, o);

    }

    public static String readLine(DataInputStream stream) throws IOException {
	StringBuilder str = new StringBuilder();
	while(true) {
	    char c = (char)stream.read();
	    if(c == '\n') break;
	    str.append(c);
	}
	return str.toString();
    }
}