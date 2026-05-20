package com.antisahir.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final List<AppInfo> appList;

    public AppAdapter(List<AppInfo> appList) {
        this.appList = appList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = appList.get(position);
        holder.tvAppName.setText(app.getAppName());
        holder.tvPackage.setText(app.getPackageName());
        holder.checkBox.setChecked(app.isSelected());

        holder.itemView.setOnClickListener(v -> {
            app.setSelected(!app.isSelected());
            holder.checkBox.setChecked(app.isSelected());
        });

        holder.checkBox.setOnClickListener(v -> {
            app.setSelected(holder.checkBox.isChecked());
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppName;
        TextView tvPackage;
        CheckBox checkBox;

        ViewHolder(View view) {
            super(view);
            tvAppName = view.findViewById(R.id.tvAppName);
            tvPackage = view.findViewById(R.id.tvPackageName);
            checkBox  = view.findViewById(R.id.checkBoxApp);
        }
    }
}
