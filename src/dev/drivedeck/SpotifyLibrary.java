package dev.drivedeck;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.ContentApi;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.types.Image;
import com.spotify.protocol.types.ListItem;
import com.spotify.protocol.types.ListItems;

/**
 * Talks to the Spotify app on the unit through the official App Remote SDK (local IPC, no
 * internet, no client secret). Gives the library screen Spotify's own "for the car" content and
 * starts playback. Needs the app registered in the Spotify dashboard with package dev.drivedeck,
 * our signing cert's SHA-1 and the redirect URI in SpotifyConfig.
 */
final class SpotifyLibrary {
    interface Callback<T> {
        void onResult(T value);
    }

    interface Status {
        void onConnected();
        void onFailure(String why);
    }

    private SpotifyAppRemote remote;
    private boolean connecting;

    boolean isConnected() {
        return remote != null && remote.isConnected();
    }

    void connect(Context c, final Status status) {
        if (isConnected()) { status.onConnected(); return; }
        if (connecting) return;
        if (!SpotifyAppRemote.isSpotifyInstalled(c)) { status.onFailure("Spotify is not installed"); return; }
        connecting = true;
        ConnectionParams params = new ConnectionParams.Builder(SpotifyConfig.CLIENT_ID)
                .setRedirectUri(SpotifyConfig.REDIRECT_URI)
                .showAuthView(true)
                .build();
        SpotifyAppRemote.connect(c, params, new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote r) {
                connecting = false;
                remote = r;
                Log.i(MainActivity.TAG, "spotify: connected");
                status.onConnected();
            }

            @Override
            public void onFailure(Throwable t) {
                connecting = false;
                Log.w(MainActivity.TAG, "spotify: connect failed", t);
                status.onFailure(explain(t));
            }
        });
    }

    /** What to tell the driver; the raw exception stays in the log. */
    private static String explain(Throwable t) {
        switch (t.getClass().getSimpleName()) {
            case "NotLoggedInException": return "Log in to Spotify on this unit, then try again";
            case "UserNotAuthorizedException": return "Allow DriveDeck in Spotify, then try again";
            case "AuthenticationFailedException": return "Spotify refused DriveDeck: check its Spotify developer settings";
            case "CouldNotFindSpotifyApp": return "Spotify is not installed";
            case "OfflineModeException": return "Spotify is in offline mode";
            case "UnsupportedFeatureVersionException": return "This Spotify version is too old";
            case "SpotifyConnectionTerminatedException":
            case "SpotifyDisconnectedException": return "Spotify disconnected: close this and open it again";
            default: return "Can't connect to Spotify";
        }
    }

    void disconnect() {
        if (remote != null) SpotifyAppRemote.disconnect(remote);
        remote = null;
    }

    /** Spotify's own shortlist for cars: e.g. recently played, made for you, playlists. */
    void recommended(final Callback<ListItem[]> cb) {
        if (!isConnected()) return;
        remote.getContentApi().getRecommendedContentItems(ContentApi.ContentType.AUTOMOTIVE)
                .setResultCallback(new CallResult.ResultCallback<ListItems>() {
                    @Override public void onResult(ListItems items) { cb.onResult(items.items); }
                })
                .setErrorCallback(logError("recommended"));
    }

    void children(ListItem parent, final Callback<ListItem[]> cb) {
        if (!isConnected()) return;
        remote.getContentApi().getChildrenOfItem(parent, 48, 0)
                .setResultCallback(new CallResult.ResultCallback<ListItems>() {
                    @Override public void onResult(ListItems items) { cb.onResult(items.items); }
                })
                .setErrorCallback(logError("children of " + parent.title));
    }

    void play(ListItem item) {
        if (!isConnected()) return;
        Log.i(MainActivity.TAG, "spotify: play " + item.uri);
        remote.getContentApi().playContentItem(item).setErrorCallback(logError("play " + item.uri));
    }

    void cover(ListItem item, final Callback<Bitmap> cb) {
        if (!isConnected() || item.imageUri == null) return;
        remote.getImagesApi().getImage(item.imageUri, Image.Dimension.THUMBNAIL)
                .setResultCallback(new CallResult.ResultCallback<Bitmap>() {
                    @Override public void onResult(Bitmap b) { cb.onResult(b); }
                })
                .setErrorCallback(logError("image"));
    }

    private static ErrorCallback logError(final String what) {
        return new ErrorCallback() {
            @Override public void onError(Throwable t) { Log.w(MainActivity.TAG, "spotify: " + what + " failed", t); }
        };
    }
}
