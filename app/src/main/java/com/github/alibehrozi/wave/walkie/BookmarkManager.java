package com.github.alibehrozi.wave.walkie;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.github.alibehrozi.wave.models.ChannelBookmark;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages saving, loading, and pre-seeding memory channel bookmarks for the Walkie Talkie.
 */
public class BookmarkManager {

    private static final String TAG = "BookmarkManager";
    private static final String PREF_NAME = "walkie_bookmarks_pref";
    private static final String KEY_BOOKMARKS = "saved_bookmarks";

    private final SharedPreferences prefs;
    private final List<ChannelBookmark> bookmarks = new ArrayList<>();

    public BookmarkManager(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadBookmarks();
    }

    private void loadBookmarks() {
        bookmarks.clear();
        String jsonStr = prefs.getString(KEY_BOOKMARKS, null);

        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            // Pre-seed default PMR446 & emergency channels
            seedDefaults();
            saveBookmarks();
            return;
        }

        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                bookmarks.add(ChannelBookmark.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse saved bookmarks", e);
            seedDefaults();
            saveBookmarks();
        }
    }

    private void seedDefaults() {
        // PMR446 Standard Channels (12.5 kHz spacing)
        bookmarks.add(new ChannelBookmark("pmr_1", "PMR 1 (Call)", 446.00625e6, 12500, "NFM", 0.0, 5, 1));
        bookmarks.add(new ChannelBookmark("pmr_2", "PMR 2", 446.01875e6, 12500, "NFM", 0.0, 5, 2));
        bookmarks.add(new ChannelBookmark("pmr_3", "PMR 3", 446.03125e6, 12500, "NFM", 0.0, 5, 3));
        bookmarks.add(new ChannelBookmark("pmr_4", "PMR 4", 446.04375e6, 12500, "NFM", 0.0, 5, 4));
        bookmarks.add(new ChannelBookmark("pmr_5", "PMR 5", 446.05625e6, 12500, "NFM", 0.0, 5, 5));
        bookmarks.add(new ChannelBookmark("pmr_6", "PMR 6", 446.06875e6, 12500, "NFM", 0.0, 5, 6));
        bookmarks.add(new ChannelBookmark("pmr_7", "PMR 7", 446.08125e6, 12500, "NFM", 0.0, 5, 7));
        bookmarks.add(new ChannelBookmark("pmr_8", "PMR 8 (Emerg)", 446.09375e6, 12500, "NFM", 0.0, 5, 8));

        // Ham & Marine
        bookmarks.add(new ChannelBookmark("ham_2m", "2M Calling", 146.520e6, 12500, "NFM", 0.0, 4, 9));
        bookmarks.add(new ChannelBookmark("marine_16", "Marine 16", 156.800e6, 25000, "NFM", 0.0, 6, 10));
    }

    private void saveBookmarks() {
        try {
            JSONArray array = new JSONArray();
            for (ChannelBookmark b : bookmarks) {
                array.put(b.toJson());
            }
            prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize bookmarks", e);
        }
    }

    @NonNull
    public List<ChannelBookmark> getBookmarks() {
        return Collections.unmodifiableList(bookmarks);
    }

    public void addBookmark(@NonNull ChannelBookmark bookmark) {
        // Avoid duplicate IDs
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).getId().equals(bookmark.getId())) {
                bookmarks.set(i, bookmark);
                saveBookmarks();
                return;
            }
        }
        bookmarks.add(bookmark);
        saveBookmarks();
    }

    public void removeBookmark(@NonNull String id) {
        bookmarks.removeIf(b -> b.getId().equals(id));
        saveBookmarks();
    }

    public boolean isFrequencyBookmarked(double freqHz) {
        for (ChannelBookmark b : bookmarks) {
            if (Math.abs(b.getFrequencyHz() - freqHz) < 500.0) {
                return true;
            }
        }
        return false;
    }
}
