package com.macarmusic.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.DocumentsContract;

// Offers real read grants from the synthetic provider's UID, just as a document picker does.
public class FixtureGrantActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String authority = "com.macarmusic.app.test.documents";
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        grantUriPermission("com.macarmusic.app",
            DocumentsContract.buildTreeDocumentUri(authority, "music"),
            flags | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        grantUriPermission("com.macarmusic.app",
            DocumentsContract.buildTreeDocumentUri(authority, "music/album"),
            flags | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        grantUriPermission("com.macarmusic.app",
            DocumentsContract.buildDocumentUri(authority, "music/one"), flags);
        grantUriPermission("com.macarmusic.app",
            DocumentsContract.buildDocumentUri(authority, "pictures/background"), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        grantUriPermission("com.macarmusic.app",
            DocumentsContract.buildDocumentUri(authority, "pictures/second"), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        grantUriPermission("com.macarmusic.app",
            DocumentsContract.buildDocumentUri(authority, "pictures/invalid"), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        finish();
    }
}
