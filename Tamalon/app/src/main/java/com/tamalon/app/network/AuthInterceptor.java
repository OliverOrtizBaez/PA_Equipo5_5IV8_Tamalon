package com.tamalon.app.network;

import android.content.Context;
import androidx.annotation.NonNull;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class AuthInterceptor implements Interceptor {
    private final TokenStore tokenStore;

    public AuthInterceptor(Context context) {
        this.tokenStore = new TokenStore(context.getApplicationContext());
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String token = tokenStore.getToken();
        if (token != null && !token.isEmpty()) {
            Request modified = original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
            return chain.proceed(modified);
        }
        return chain.proceed(original);
    }
}
