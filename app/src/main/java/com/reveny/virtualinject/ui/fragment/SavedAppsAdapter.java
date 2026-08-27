package com.reveny.virtualinject.ui.fragment;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.reveny.virtualinject.R;

import java.util.List;

public class SavedAppsAdapter extends RecyclerView.Adapter<SavedAppsAdapter.ViewHolder> {

    public interface OnAppActionListener {
        void onLaunch(SavedConfig config);
        void onDelete(int position);
    }

    private final List<SavedConfig> configs;
    private final Context context;
    private final OnAppActionListener listener;

    public SavedAppsAdapter(Context context, List<SavedConfig> configs, OnAppActionListener listener) {
        this.context = context;
        this.configs = configs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_saved_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavedConfig config = configs.get(position);

        // App Name
        holder.appName.setText(config.packageName);

        // Library count
        int count = config.libraryPaths.size();
        holder.libCount.setText(count + " librar" + (count == 1 ? "y" : "ies") + " loaded");

        // App Icon from system PackageManager
        try {
            PackageManager pm = context.getPackageManager();
            Drawable icon = pm.getApplicationIcon(config.packageName);
            holder.appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // Launch
        holder.launchBtn.setOnClickListener(v -> listener.onLaunch(config));

        // Delete
        holder.deleteBtn.setOnClickListener(v -> listener.onDelete(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return configs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName, libCount;
        MaterialButton launchBtn, deleteBtn;

        ViewHolder(View itemView) {
            super(itemView);
            appIcon   = itemView.findViewById(R.id.app_icon);
            appName   = itemView.findViewById(R.id.app_name);
            libCount  = itemView.findViewById(R.id.lib_count);
            launchBtn = itemView.findViewById(R.id.launch_btn);
            deleteBtn = itemView.findViewById(R.id.delete_btn);
        }
    }
}
