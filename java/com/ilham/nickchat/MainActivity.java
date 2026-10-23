package com.ilham.nickchat;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Nickchat for Android. The messenger itself is the web app in assets/web/index.html,
 * so every function is identical on every platform. This class gives it a native window
 * and connects the few things a web page cannot do alone: notifications, saving the
 * backup file, choosing a backup to restore, the Back button, and opening links.
 */
public class MainActivity extends Activity {
    private static final String CHANNEL = "messages";
    private static final int REQ_NOTIFY = 1;
    private static final int REQ_FILE = 2;
    private static final int REQ_MEDIA = 3;
    private PermissionRequest pendingMedia;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private int notificationId = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createChannel();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // chats are stored here
        s.setAllowFileAccess(true);            // the page is loaded from the app's assets
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);

        web.addJavascriptInterface(new Bridge(), "NickchatNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)
                        || "mailto".equals(scheme) || "tel".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // Voice and video calls: pass the microphone and camera request on to Android.
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> handleMedia(request));
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "Choose a backup file"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/web/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Let the page close an open chat or dialog first. If there is nothing to close,
        // send the app to the background so messages keep arriving.
        web.evaluateJavascript("(window.nickchatBack&&window.nickchatBack())?'1':'0'", value -> {
            if (!"\"1\"".equals(value)) {
                moveTaskToBack(true);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    private void handleMedia(PermissionRequest request) {
        java.util.ArrayList<String> need = new java.util.ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.CAMERA);
            }
        }
        if (need.isEmpty()) {
            request.grant(request.getResources());
            return;
        }
        if (pendingMedia != null) {
            pendingMedia.deny();
        }
        pendingMedia = request;
        requestPermissions(need.toArray(new String[0]), REQ_MEDIA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MEDIA && pendingMedia != null) {
            boolean ok = results.length > 0;
            for (int r : results) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    ok = false;
                }
            }
            if (ok) {
                pendingMedia.grant(pendingMedia.getResources());
            } else {
                pendingMedia.deny();
            }
            pendingMedia = null;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Messages", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("New messages");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private boolean canNotify() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showNotification(String title, String body) {
        if (!canNotify()) {
            return;
        }
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true);

        notificationId = notificationId >= 1049 ? 1000 : notificationId + 1;
        getSystemService(NotificationManager.class).notify(notificationId, builder.build());
    }

    private void saveBackup(String name, String text) {
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    throw new IllegalStateException("no uri");
                }
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        throw new IllegalStateException("no stream");
                    }
                    out.write(data);
                }
                toast("Backup saved in your Downloads folder: " + name);
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(data);
                }
                toast("Backup saved: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            toast("Could not save the backup");
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    /** The functions the web page can call as window.NickchatNative.* */
    private class Bridge {
        @JavascriptInterface
        public void showNotification(String title, String body) {
            runOnUiThread(() -> MainActivity.this.showNotification(title, body));
        }

        @JavascriptInterface
        public void requestNotifyPermission() {
            runOnUiThread(() -> {
                if (!canNotify()) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String name, String text) {
            runOnUiThread(() -> saveBackup(name, text));
        }
    }
}
