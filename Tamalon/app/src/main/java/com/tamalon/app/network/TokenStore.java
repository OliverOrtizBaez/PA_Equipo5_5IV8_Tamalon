package com.tamalon.app.network;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.google.gson.Gson;
import java.time.Instant;

public class TokenStore {
    private static final String PREF_NAME = "tamalon_secure_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_EXPIRES = "auth_expires";
    private static final String KEY_USER_JSON = "auth_user_json";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public TokenStore(Context context) {
        SharedPreferences tempPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            tempPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            tempPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = tempPrefs;
    }

    public void saveSession(String token, String expires, DtoUser dtoUser) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_EXPIRES, expires)
                .putString(KEY_USER_JSON, gson.toJson(dtoUser))
                .apply();
    }

    public void saveUser(DtoUser dtoUser) {
        saveSession(getToken(), getExpires(), dtoUser);
    }

    public void updatePlanAndRol(String plan, String rol) {
        DtoUser user = getUser();
        if (user != null) {
            if (plan != null) user.plan = plan;
            if (rol != null) user.rol = rol;
            saveUser(user);
        }
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getExpires() {
        return prefs.getString(KEY_EXPIRES, null);
    }

    public DtoUser getUser() {
        String json = prefs.getString(KEY_USER_JSON, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, DtoUser.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isSessionActive() {
        String token = getToken();
        String expires = getExpires();
        if (token == null || token.isEmpty()) return false;
        if (expires == null || expires.isEmpty()) return true;
        try {
            Instant expInstant = Instant.parse(expires);
            return Instant.now().isBefore(expInstant);
        } catch (Exception e) {
            return true;
        }
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
