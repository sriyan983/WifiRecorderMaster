package com.portfolio.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.instacart.library.truetime.TrueTime;

import java.io.IOException;

public class InitTrueTimeAsyncTask extends AsyncTask<Void, Void, Void> {
    private Context ctx;

    public InitTrueTimeAsyncTask (Context context){
        ctx = context;
    }

    protected Void doInBackground(Void... params) {
        try {
            TrueTime.build()
                    .withSharedPreferencesCache(ctx)
                    .withNtpHost("time.google.com")
                    .withLoggingEnabled(false)
                    .withConnectionTimeout(31_428)
                    .initialize();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("InitTrueTimeAsyncTask", "Exception when trying to get TrueTime", e);
        }
        return null;
    }
}
