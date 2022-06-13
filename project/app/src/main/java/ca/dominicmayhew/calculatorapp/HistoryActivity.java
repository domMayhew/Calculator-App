package ca.dominicmayhew.calculatorapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    List<String> history;
    RecyclerView rv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        history = getIntent().getStringArrayListExtra(MainActivity.HISTORY_LIST_EXTRA_KEY);
        if (history == null) {
            history = new ArrayList<>();
        }

        rv = findViewById(R.id.historyRv);
        rv.setAdapter(new HistoryAdapter(history));
    }

    public void clearHistoryListener(View v) {
        history.clear();
        rv.getAdapter().notifyDataSetChanged();
        Intent i = new Intent(MainActivity.ACTION_CLEAR_HISTORY);
//        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        sendBroadcast(i);
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        List<String> history;

        public class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView textView;

            public ViewHolder(View view) {
                super(view);
                textView = view.findViewById(R.id.listViewTv);

                view.setOnClickListener((View v) -> {
                    Intent i = new Intent(HistoryActivity.this, MainActivity.class);
                    i.putExtra(MainActivity.EXPRESSION_EXTRA_KEY, textView.getText().toString());
                    startActivity(i);
                });
            }

            public TextView getTextView() { return textView; }
        }

        HistoryAdapter(List<String> history) {
            this.history = history;
        }

        @NonNull
        @Override
        public HistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryAdapter.ViewHolder holder, int position) {
            holder.getTextView().setText(history.get(position));
        }

        @Override
        public int getItemCount() {
            return history.size();
        }
    }
}