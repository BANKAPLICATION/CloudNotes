package com.cloudnotes.app.utility;

import android.content.Context;
import android.content.Intent;

public class ShareUtils {
    public static void shareContent(Context context, String content, String title) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, content);

        context.startActivity(Intent.createChooser(intent, title));
    }
}
