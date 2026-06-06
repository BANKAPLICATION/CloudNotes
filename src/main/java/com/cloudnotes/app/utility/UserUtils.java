package com.cloudnotes.app.utility;

import android.content.Context;

import com.amplifyframework.auth.AuthUserAttribute;
import com.amplifyframework.core.Amplify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserUtils {

    public interface OnUserDetailsFetchedListener {
        void onUserDetailsFetched(Map<String, String> attributes);

        void onError(Exception exception);
    }

    public static void fetchUserDetails(Context context, OnUserDetailsFetchedListener listener) {
        Amplify.Auth.fetchUserAttributes(
                attributes -> {
                    Map<String, String> map = new HashMap<>();
                    for (AuthUserAttribute attribute : attributes) {
                        map.put(attribute.getKey().getKeyString(), attribute.getValue());
                    }
                    listener.onUserDetailsFetched(map);
                },
                error -> listener.onError(new Exception(error.getMessage()))
        );
    }
}
