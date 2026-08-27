package com.reveny.virtualinject.ui.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.reveny.virtualinject.BuildConfig;
import com.reveny.virtualinject.R;
import com.reveny.virtualinject.databinding.DialogAboutBinding;
import com.reveny.virtualinject.databinding.FragmentHomeBinding;
import com.reveny.virtualinject.ui.dialog.BlurBehindDialogBuilder;
import com.reveny.virtualinject.util.Utility;
import com.reveny.virtualinject.util.chrome.LinkTransformationMethod;
import com.vcore.BlackBoxCore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import rikka.material.app.LocaleDelegate;

public class HomeFragment extends BaseFragment {
    private static final String TAG = "VirtualInjectLog";
    private static final String PREFS_NAME = "VirtualInjectPrefs";
    private static final String KEY_CONFIGS = "saved_configs";

    private String selectedApp;
    private final List<String> selectedLibraryPaths = new ArrayList<>();
    private final List<SavedConfig> savedConfigs = new ArrayList<>();
    private SavedAppsAdapter adapter;
    private FragmentHomeBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.about).setOnMenuItemClickListener(item -> {
            showAbout();
            return true;
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != 1 || resultCode != Activity.RESULT_OK || data == null) return;

        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) uris.add(clipData.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        selectedLibraryPaths.clear();
        int successCount = 0;

        for (Uri fileUri : uris) {
            String path = fileUri.getPath();
            if (path == null) continue;
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            File dest = new File(requireContext().getCacheDir(), fileName);

            try (InputStream in = requireContext().getContentResolver().openInputStream(fileUri);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) out.write(buffer, 0, bytesRead);
                selectedLibraryPaths.add(dest.getAbsolutePath());
                successCount++;
                Log.i(TAG, "Copied: " + fileName);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy: " + fileName, e);
            }
        }

        if (successCount > 0) binding.libPath.setText(successCount + " file(s) selected");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        setupToolbar(binding.toolbar, null, R.string.app_name, R.menu.menu_home);
        binding.toolbar.setNavigationIcon(null);
        binding.toolbar.setOnClickListener(null);
        binding.appBar.setLiftable(true);
        binding.nestedScrollView.getBorderViewDelegate().setBorderVisibilityChangedListener(
            (top, oldTop, bottom, oldBottom) -> binding.appBar.setLifted(!top));
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        setupApplist();
        setupRecyclerView();
        loadConfigs();

        binding.libPathChoose.setEndIconOnClickListener(v -> {
            Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
            chooseFile.setType("*/*");
            chooseFile.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream"});
            chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            chooseFile = Intent.createChooser(chooseFile, "Select .so files");
            startActivityForResult(chooseFile, 1);
        });

        binding.installButton.setOnClickListener(v -> {
            if (selectedApp == null) {
                Toast.makeText(requireContext(), "Please select an app", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedLibraryPaths.isEmpty()) {
                Toast.makeText(requireContext(), "Please select library files", Toast.LENGTH_SHORT).show();
                return;
            }

            BlackBoxCore.get().installPackageAsUser(selectedApp, 0);
            if (!BlackBoxCore.get().isInstalled(selectedApp, 0)) {
                Toast.makeText(requireContext(), "Failed to install", Toast.LENGTH_SHORT).show();
                return;
            }

            saveConfig(new SavedConfig(selectedApp, selectedLibraryPaths));
            Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show();

            binding.appSelectorText.setText("");
            binding.libPath.setText("");
            selectedApp = null;
            selectedLibraryPaths.clear();
        });

        return binding.getRoot();
    }

    private void launchApp(SavedConfig config) {
        if (!BlackBoxCore.get().isInstalled(config.packageName, 0)) {
            Toast.makeText(requireContext(), "App not installed, please reinstall", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "Launching: " + config.packageName);
        Log.i(TAG, "Libraries: " + config.libraryPaths.toString());

        // الحقن يحدث تلقائياً عبر IO redirect داخل launchApk
        BlackBoxCore.get().launchApk(config.packageName, 0);
    }

    private void setupRecyclerView() {
        adapter = new SavedAppsAdapter(requireContext(), savedConfigs, new SavedAppsAdapter.OnAppActionListener() {
            @Override
            public void onLaunch(SavedConfig config) {
                launchApp(config);
            }

            @Override
            public void onDelete(int position) {
                savedConfigs.remove(position);
                adapter.notifyItemRemoved(position);
                persistConfigs();
                Toast.makeText(requireContext(), "Removed", Toast.LENGTH_SHORT).show();
            }
        });
        binding.savedAppsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.savedAppsList.setAdapter(adapter);
    }

    private void saveConfig(SavedConfig config) {
        for (int i = 0; i < savedConfigs.size(); i++) {
            if (savedConfigs.get(i).packageName.equals(config.packageName)) {
                savedConfigs.set(i, config);
                adapter.notifyItemChanged(i);
                persistConfigs();
                return;
            }
        }
        savedConfigs.add(config);
        adapter.notifyItemInserted(savedConfigs.size() - 1);
        persistConfigs();
    }

    private void persistConfigs() {
        try {
            JSONArray arr = new JSONArray();
            for (SavedConfig c : savedConfigs) arr.put(c.toJson());
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_CONFIGS, arr.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save configs", e);
        }
    }

    private void loadConfigs() {
        try {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_CONFIGS, "[]");
            JSONArray arr = new JSONArray(json);
            savedConfigs.clear();
            for (int i = 0; i < arr.length(); i++) savedConfigs.add(SavedConfig.fromJson(arr.getJSONObject(i)));
            adapter.notifyDataSetChanged();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load configs", e);
        }
    }

    private void setupApplist() {
        List<String> installedApps = Utility.getInstalledApps(requireContext());
        ArrayAdapter<String> appAdapter = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_dropdown_item_1line, installedApps);
        binding.appSelectorText.setAdapter(appAdapter);
        binding.appSelectorText.setOnItemClickListener((parent, view, position, id) ->
            selectedApp = (String) parent.getItemAtPosition(position));
        binding.appSelectorText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) return;
            String currText = binding.appSelectorText.getText().toString();
            if (installedApps.stream().noneMatch(c -> c.equals(currText))) {
                binding.appSelectorText.setText("");
                selectedApp = null;
            }
        });
    }

    public static class AboutDialog extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            DialogAboutBinding binding = DialogAboutBinding.inflate(getLayoutInflater(), null, false);
            binding.designAboutTitle.setText(R.string.app_name);
            binding.designAboutInfo.setMovementMethod(LinkMovementMethod.getInstance());
            binding.designAboutInfo.setTransformationMethod(new LinkTransformationMethod(requireActivity()));
            binding.designAboutInfo.setText(HtmlCompat.fromHtml(getString(
                    R.string.about_view_source_code,
                    "<b><a href=\"https://t.me/revenyy\">Telegram</a></b>",
                    "<b><a href=\"https://github.com/reveny/\">Reveny</a></b>"),
                    HtmlCompat.FROM_HTML_MODE_LEGACY));
            binding.designAboutVersion.setText(String.format(LocaleDelegate.getDefaultLocale(),
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            return new BlurBehindDialogBuilder(requireContext()).setView(binding.getRoot()).create();
        }
    }

    private void showAbout() {
        new AboutDialog().show(getChildFragmentManager(), "about");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
