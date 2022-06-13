package ca.dominicmayhew.calculatorapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ClearHistoryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == MainActivity.ACTION_CLEAR_HISTORY) {
            ((MainActivity) context).clearHistory();
        }
    }
}