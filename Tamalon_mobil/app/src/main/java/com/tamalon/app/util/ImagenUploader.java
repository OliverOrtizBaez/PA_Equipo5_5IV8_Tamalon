package com.tamalon.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.tamalon.app.R;
import com.tamalon.app.network.ApiClient;
import com.tamalon.app.network.ApiService;
import com.tamalon.app.network.ImagenUploadResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Utilidad para comprimir, muestrear, corregir orientación EXIF y subir imágenes
 * mediante multipart/form-data al backend REST.
 */
public class ImagenUploader {

    public interface UploadCallback {
        void onSuccess(String url);
        void onError(String mensaje);
    }

    public static void subirImagen(Context context, Uri uri, String destino, UploadCallback callback) {
        if (uri == null) {
            callback.onSuccess(null);
            return;
        }

        try {
            File compressedFile = procesarYComprimirImagen(context, uri);
            if (compressedFile == null || !compressedFile.exists()) {
                callback.onError(context.getString(R.string.image_upload_error));
                return;
            }

            RequestBody requestFile = RequestBody.create(compressedFile, MediaType.parse("image/jpeg"));
            MultipartBody.Part body = MultipartBody.Part.createFormData("imagen", compressedFile.getName(), requestFile);
            RequestBody destinoBody = RequestBody.create(destino, MediaType.parse("text/plain"));

            ApiService apiService = ApiClient.getApiService(context);
            apiService.subirImagen(body, destinoBody).enqueue(new Callback<ImagenUploadResponse>() {
                @Override
                public void onResponse(@NonNull Call<ImagenUploadResponse> call, @NonNull Response<ImagenUploadResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().url != null) {
                        callback.onSuccess(response.body().url);
                    } else {
                        int code = response.code();
                        String msg;
                        if (code == 413) {
                            msg = context.getString(R.string.image_too_large);
                        } else if (code == 400) {
                            msg = context.getString(R.string.image_bad_request);
                        } else if (code == 503) {
                            msg = context.getString(R.string.image_service_unavailable);
                        } else {
                            msg = context.getString(R.string.image_upload_error);
                        }
                        callback.onError(msg);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ImagenUploadResponse> call, @NonNull Throwable t) {
                    callback.onError(context.getString(R.string.image_network_error));
                }
            });

        } catch (Exception e) {
            callback.onError(context.getString(R.string.image_upload_error));
        }
    }

    private static File procesarYComprimirImagen(Context context, Uri uri) throws Exception {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {}

        BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
        boundsOpts.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, boundsOpts);
        }

        int width = boundsOpts.outWidth;
        int height = boundsOpts.outHeight;
        int inSampleSize = 1;
        int maxSide = 1600;

        while ((width / inSampleSize) > maxSide || (height / inSampleSize) > maxSide) {
            inSampleSize *= 2;
        }

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = inSampleSize;
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, decodeOpts);
        }

        if (bitmap == null) return null;

        bitmap = rotarSegunExif(bitmap, orientation);
        bitmap = escalarMax1600(bitmap, maxSide);

        File outputDir = context.getCacheDir();
        File outputFile = File.createTempFile("upload_", ".jpg", outputDir);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();
        } finally {
            bitmap.recycle();
        }

        return outputFile;
    }

    private static Bitmap rotarSegunExif(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            return bitmap;
        }
    }

    private static Bitmap escalarMax1600(Bitmap src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxSide && h <= maxSide) return src;
        float ratio = Math.min((float) maxSide / w, (float) maxSide / h);
        int nw = Math.max(1, Math.round(w * ratio));
        int nh = Math.max(1, Math.round(h * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);
        if (scaled != src) {
            src.recycle();
        }
        return scaled;
    }
}
